# sparql-markdown

sparql-markdown is markdown converter to append result of SPARQL.

## Getting Started

### Work on your PC

Download from jitpack.

```
wget https://jitpack.io/com/github/takemikami/sparql-markdown/<version>/sparql-markdown-<version>-all.jar
java -jar sparql-markdown-<version>-all.jar --help
```

Execute command on directory placed rdfxml or turtle files.

```
find . -name "*.md" | xargs java -jar sparql-markdown-<version>-all.jar --replace --files
```

Usage:

```
usage: sparql-markdown [options]
    --clear             Clear Result of Markdown file
    --files <file(s)>   Markdown files
    --help              Print usage
    --replace           Replace Markdown file
    --targetdir <arg>   Target Directory Path
```

### Work on GitHub Actions

Make .github/workflows/sparqlmarkdown.yml to your repository. Its contents is following.

```
name: sparqlmarkdown
on:
  push:

jobs:
  sparqlmarkdown:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v2
    - uses: actions/setup-java@v1
      with:
        java-version: 16
    - name: setup sparql-markdown
      run: wget https://jitpack.io/com/github/takemikami/sparql-markdown/<version>/sparql-markdown-<version>-all.jar
    - name: update markdown
      run: find . -name "*.md" | xargs java -jar sparql-markdown-<version>-all.jar --replace --files
```

## Contributing

Build by gradle.

```
./gradlew shadowJar
```

Run by gradle.

print usage.

```
./gradlew run --args="--help"
```

process sparql.

```
./gradlew run --args="--targetdir <rdfxml/turtle dir path> --files <markdown file>"
```
