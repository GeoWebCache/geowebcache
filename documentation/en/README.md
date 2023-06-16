# GeoWebCache Documentation

GeoWebCache documentation:

* ``user/`` documentation files
* ``user/source/config.py`` - documentation build settings
* ``user/source/index.rst`` - first page of documentation 
* ``target/user/html/`` - build output 
* ``target/user/html/index.html`` - first page of generated html

## Ant Build

An ant ``build.xml`` script provided (note the python environment described below is required).

To generate html documentation:

```
ant docs
```

This uses ``sphinx-build`` to generate documentation into: ``target/user/html/index.html``

To view content while editing:

```
ant site
```

This uses ``sphinx-autobuild`` to serve docs on next available port, opening a browser to review generated pages. The browser will refresh as pages are edited and saved.

To package documentation bundle:
```
ant package
```

Contents are packaged into: ``target/geowebcache_1.23-SNAPSHOT-docs.zip``

## Make Build

A ``Makefile`` and ``make.bat`` file is provided:
```
cd user
make html
```

Contents are generated into:
```
open build/html/index.html
```

## Python and Sphinx Setup

The documentation is written with [sphinx-build](https://www.sphinx-doc.org/en/master/), which is a Python documentation generator.

Install Python (macOS example):
```
brew install python
```

To install ``sphinx-build`` and ``sphinx-autobuild`` using ``requirements.txt``:
```
pip3 install -r requirements.txt
```

To confirm installation:
```
sphinx-build --version
```

## Python Virtual Environment Setup

Optional: To establish a virtual environment just for this project (macOS example):

```
brew install virtualenv
virtualenv venv
```

To activate python:
```
source venv/bin/activate
```

To install requirements into virtual environment:
```
pip install -r requirements.txt
```

To confirm installation:
```
sphinx-build --version
```

