package play.modules.es6;

import play.Play;
import play.PlayPlugin;
import play.exceptions.CompilationException;
import play.mvc.Http;
import play.templates.Template;
import play.templates.TemplateLoader;
import play.utils.FastRuntimeException;
import play.vfs.VirtualFile;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ES6Plugin extends PlayPlugin {
    private static final class CompiledES6 {
        public final Long sourceLastModified;  // Last modified time of the VirtualFile
        public final String output;  // Compiled es6

        public CompiledES6(Long sourceLastModified, String output) {
            this.sourceLastModified = sourceLastModified;
            this.output = output;
        }
    }

    // Regex to get the line number of the failure.
    private static final Pattern LINE_NUMBER = Pattern.compile("line ([0-9]+)");

    private static final Pattern MOBILE_CHECKER = Pattern
            .compile("Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini");
    private static final ThreadLocal<SimpleBindings> bindings = new ThreadLocal<SimpleBindings>() {
        @Override
        protected SimpleBindings initialValue() {
            return new SimpleBindings();
        }
    };
    private static final ThreadLocal<ScriptEngine> compiler = new ThreadLocal<ScriptEngine>() {
        @Override
        protected ScriptEngine initialValue() {
            try {
                ScriptEngine engine = new ScriptEngineManager().getEngineByMimeType("text/javascript");
                engine.eval(
                        new FileReader(String.format("%s/%s", Play.applicationPath, "public/javascripts/babel.min.js")),
                        getBindings());
                return engine;
            } catch(FileNotFoundException | ScriptException e) {
                return null;
            }
        }
    };
    private Map<String, CompiledES6> cache;  // Map of Relative Path -> Compiled es6

    /**
     * @return the line number that the exception happened on, or 0 if not found in the message.
     */
    public static int getLineNumber(Exception e) {
        Matcher m = LINE_NUMBER.matcher(e.getMessage());
        if(m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    /**
     * check mobile device
     *
     * @param userAgent
     * @return
     */
    public boolean mobileCheck(String userAgent) {
        return userAgent != null && MOBILE_CHECKER.matcher(userAgent).find();
    }

    public static ScriptEngine getCompiler() {
        return compiler.get();
    }

    public static SimpleBindings getBindings() {
        return bindings.get();
    }

    @Override
    public void onLoad() {
        cache = new HashMap<>();
    }

    @Override
    public boolean serveStatic(VirtualFile file, Http.Request request, Http.Response response) {
        if(file.getName().endsWith(".es6") && mobileCheck(
                Optional.ofNullable(request.headers.get("user-agent")).map(Http.Header::toString).orElse(null))) {
            try {
                response.contentType = "text/javascript";
                response.status = 200;
                if(Play.mode == Play.Mode.PROD) {
                    response.cacheFor("1h");
                }

                // Check the cache.
                String relativePath = file.relativePath();
                CompiledES6 ce = cache.get(relativePath);
                if(ce != null && ce.sourceLastModified.equals(file.lastModified())) {
                    response.print(ce.output);
                    return true;
                }

                SimpleBindings bindings = getBindings();
                bindings.put("input", file.contentAsString());
                // Compile the es6 and return.
                Object output = getCompiler().eval("Babel.transform(input, { presets: ['es2015'] }).code", bindings);
                if(output == null) throw new FastRuntimeException("compilation error!");

                String compiledES6 = output.toString();
                cache.put(relativePath, new CompiledES6(file.lastModified(), compiledES6));
                response.print(compiledES6);
            } catch(ScriptException | FastRuntimeException e) {
                // Render a nice error page.
                Template tmpl = TemplateLoader.load("errors/500.html");
                Map<String, Object> args = new HashMap<>();
                Exception ex = new CompilationException(file, e.getMessage(), getLineNumber(e), -1, -1);
                args.put("exception", ex);
                play.Logger.error(ex, "ES6 compilation error");
                response.contentType = "text/html";
                response.status = 500;
                response.print(tmpl.render(args));
            }
            return true;
        } else {
            return super.serveStatic(file, request, response);
        }
    }
}