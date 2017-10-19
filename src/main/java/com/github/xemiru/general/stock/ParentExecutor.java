package com.github.xemiru.general.stock;

import com.github.xemiru.general.*;
import com.github.xemiru.general.exception.CommandException;

import java.util.*;

import static com.github.xemiru.general.ArgumentParsers.STRING;

/**
 * An implementation of {@link CommandExecutor} that acts as a parent command to numerous subcommands.
 *
 * <p>Comes with a built-in help command, generated from {@link HelpExecutor#create(List)}.</p>
 *
 * <p>If you need to build a command using this executor, it may be better to make use of {@link Command#parent()}.</p>
 */
public class ParentExecutor implements CommandExecutor {

    public static class CommandMatcher implements ArgumentParser<Optional<CommandContext>> {

        private CommandContext parent;
        private List<Command> commands;

        CommandMatcher(CommandContext parent, List<Command> commands) {
            this.parent = parent;
            this.commands = commands;
        }

        @Override
        public String getTypename() {
            return "command";
        }

        @Override
        public Optional<CommandContext> parse(RawArguments args) {
            String name = STRING.parse(args);
            for (Command cmd : this.commands) {
                if (cmd.hasName(name))
                    return Optional.of(new CommandContext(this.parent.getManager(), cmd, name, this.parent.isDry()));
            }

            return Optional.empty();
        }

        @Override
        public Set<String> getSuggestions() {
            Set<String> returned = new LinkedHashSet<>();
            for (Command cmd : this.commands) returned.add(cmd.getName());
            return returned;
        }
    }

    private List<Command> commands;

    public ParentExecutor() {
        this.commands = new ArrayList<>();
        this.commands.add(HelpExecutor.create(this.commands));
    }

    /**
     * Adds subcommands to this {@link ParentExecutor}'s selection.
     *
     * @param commands commands to add
     * @return this ParentExecutor
     */
    public ParentExecutor addCommands(Command... commands) {
        return this.addCommands(Arrays.asList(commands));
    }

    /**
     * Adds subcommands to this {@link ParentExecutor}'s selection.
     *
     * @param commands commands to add
     * @return this ParentExecutor
     */
    public ParentExecutor addCommands(Collection<Command> commands) {
        this.commands.addAll(commands);
        return this;
    }

    @Override
    public void execute(CommandContext context, Arguments args, boolean dry) {
        args.write(new CommandMatcher(context, this.commands));
        if (dry) {
            // be a little hacky; we need to call the command dry to harvest the syntax
            // make a new context that lies about being dry-ran so we can get the context
            Arguments lie = args.copy().setContext(new CommandContext(context.getManager(), null, null, false));

            Optional<CommandContext> ctx = lie.next();
            ctx.ifPresent(commandContext -> commandContext.getCommand().getExec()
                .execute(commandContext, args.drop(1).setContext(commandContext), true));
        } else {
            Optional<CommandContext> ctx = args.next();
            if (!ctx.isPresent()) {
                String suggest = context.getLabel() == null ? "help" : context.getLabel() + " help";
                throw new CommandException(String.format("Unknown command. Try \"%s\".", suggest));
            }

            ctx.get().getCommand().getExec().execute(ctx.get(), args.drop(1).setContext(ctx.get()), false);
        }
    }

}
