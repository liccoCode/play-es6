# ES6 Play! plugin.

## Background

[ES6](http://es6-features.org/)

## Overview

This module integrates [Play!](http://www.playframework.org) with [ES6](http://es6-features.org/).  It uses
[babel-standalone](https://github.com/Daniel15/babel-standalone) to run the ES6 compiler.

### Caching

In general, ES6 is only re-compiled if it has changed since the last request (based on the file's last-modified date).
In the absence of changes to the source, the compiled ES6 is cached forever.

In production mode, a cache header is also set telling the client to cache the file for 1 hour.

### Compilation errors

A compilation error in an included file will cause the error to be logged and will return 500.  If the developer visits the resource link directly, he can see the useful compilation error screen showing the offending line.

## Getting started

This module is not yet on www.playframework.org.  

To use it in your Play! project:

1. Add - es6 -> es6 0.1 in your dependencies.yml require section.
2. Specify new custom repositories in the your dependencies.yml repositories section:
    
    ```yaml
    - es6:
        type: HTTP
        artifact: "https://raw.github.com/kenyonduan/play-es6/master/dist/[module]-[revision].zip"
        contains:
            - es6 -> *
    ```
3. Run 'play dependencies' in your app's directory to download it.
