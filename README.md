# General

![Travis CI Status](https://travis-ci.org/Xemiru/General.svg?branch=develop)

A worry-free command system.

General'll let you worry only about your own command logic. Everything else, from argument parsing, to syntax generation, to even the basic help command is done for you. See the Quick-start section below for a quick breakdown of what it can do, or head to the wiki to learn more!

Java documentation for the latest version can be found at [https://xemiru.github.io/General/latest/](https://xemiru.github.io/General/latest/). `latest` can be replaced with a release version number to see that version's javadocs.

#### Gradle
```groovy
// build.gradle

repositories {
    mavenCentral()
    // or if you need snapshot versions
    maven { url = 'https://oss.sonatype.org/content/repositories/snapshots' }
}

dependencies {
    // replace VERSION with the version you need
    compile "com.github.xemiru:general:VERSION"
}
```

## Quick-start

#### Declare what a command does ...

```java
import static com.github.xemiru.general.ArgumentParsers.*;

import com.github.xemiru.general.Arguments;
import com.github.xemiru.general.CommandContext;
import com.github.xemiru.general.FullCommandExecutor;

import com.github.xemiru.general.exception.CommandException;

public class MyExecutor implements FullCommandExecutor {

    @Override
    public void initialize(CommandContext context, Arguments args) {
        // Ask for arguments!
        // Syntax will be generated for you.
        args.write(NUMBER)
            .write(NUMBER);
    }

    @Override
    public void execute(CommandContext context, Arguments args) {
        double a = args.next(); // Use them in the order you asked for!
        double b = args.next();
        double sum = a + b;

        if(sum > 10) throw new CommandException("Too big!");
        context.sendMessage(String.format("%s + %s = %s", a, b, sum));
    }

}

```

#### Create the command ...

```java
import com.github.xemiru.general.Command;

// ...

Command add = Command.builder()
    .name("add")
    .shortDescription("Addition!")
    .description("Adds two numbers you give to it .. but only up to 10.")
    .executor(new MyExecutor()).build();
```

#### Run it in a command manager!

```java
import com.github.xemiru.general.CommandManager;

// Command add = ... (see above)
// ...

CommandManager cmdMan = new CommandManager();
cmdMan.addCommands(add);

// ...

String userIn = "add 2 3"; // pretend a user wrote this
cmdMan.sendMessage("> " + userIn);
cmdMan.handleCommand(userIn);
```

```
> add 2 3
2.0 + 3.0 = 5.0
```

#### The user could screw up, but you don't need to worry.

```
> add 2a 3
not a number: 2a
Syntax: add <number> <number>

> add 5 9
Too big!
```

[General is also ready for command completion](https://github.com/Xemiru/General/wiki/Argument-Parsers#parameter-completion).
