# ebean-idea-enhancer
IntelliJ IDEA plugin to perform EbeanORM Enhancement

Ebean enhancement plugin for IntelliJ IDEA - Adds post-compile Ebean enhancement.

Based on the previous work by Mario Ivankovits and updated by Yevgeny Krasik for IntelliJ IDEA 13+.

### Running/Debugging

Use 
```
./gradlew runIde
```
for running the plugin.

Use the same gradle task (optionally running it with debug) to do the same from within IntelliJ.


### Building

```
./gradlew buildPlugin
```

### Releasing

```
./gradlew release
```

This will tag, build and the file will be available in ```build/distributions/ebean-idea-enhancer-*.zip```.

#### Release with uploading to jetbrains

First create ``~/.gradle/gradle.properties``` and add the following lines (with correct data). This is necessary for
uploading to JetBrains repository.
```
intellij.username=<username>
intellij.password=<password>
```

After that, run:
```
./gradlew -PwithUpload release
```

This will tag, build, push to jetbrains and the file
will also available in ```build/distributions/ebean-idea-enhancer-*.zip```.
