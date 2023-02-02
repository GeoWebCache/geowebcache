# GeoWebCache Documentation

## Building

An ant build script is required (note the python environment described below is required).

```
ant docs
```

For feedback while editing:
```
ant site
```

## Setup Python Virtual Environment

Using `virtualenv`` (macos example):

```
brew install virtualenv
virtualenv venv
```

To activate python:

```
source venv/bin/activate
```

To configure this environment with ``sphinx-build`` (and any other ``requirements.txt``):
```
pip install -r requirements.txt
```

To confirm installation:
```
sphinx-build --version
```
