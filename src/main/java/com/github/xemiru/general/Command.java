package com.github.xemiru.general;

import com.github.xemiru.general.stock.ParentExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Command {

    // region builder classes

    // ask me what this generic is for i fuckin dare you
    // (removing code duplication)
    public static class Builder<T extends Builder> {

        Command cmd;

        private Builder() {
            this.cmd = new Command();
        }

        /**
         * Required. The name of the command and other alternate names.
         *
         * @param name the main name of the command
         * @param aliases the aliases of the command
         * @return this builder
         */
        public T name(String name, String... aliases) {
            String[] names = new String[1 + aliases.length];
            names[0] = name;

            System.arraycopy(aliases, 0, names, 1, aliases.length);
            this.cmd.names = names;
            return (T) this;
        }

        /**
         * Optional. The static syntax of the command.
         *
         * <p>If this is set, syntax errors will show the set syntax instead of using the syntax generated by the
         * command's executor.</p>
         *
         * @param syntax the static syntax of the command
         * @return this builder
         */
        public T syntax(String syntax) {
            this.cmd.syntax = syntax;
            return (T) this;
        }

        /**
         * Optional. The short description of the command.
         *
         * @param desc the short description of the command
         * @return this builder
         */
        public T shortDescription(String desc) {
            this.cmd.shortDesc = desc;
            return (T) this;
        }

        /**
         * Optional. The full description of the command.
         *
         * @param desc the full description of the command
         * @return this builder
         */
        public T description(String desc) {
            this.cmd.description = desc;
            return (T) this;
        }

        /**
         * Builds the command.
         *
         * @return the resulting Command
         */
        public Command build() {
            check(this.cmd.names, "Command must have a name");
            check(this.cmd.exec, "Command must have an executor");
            return this.cmd;
        }

        private void check(Object maybeNull, String error) {
            if (maybeNull == null) throw new IllegalStateException(error);
        }

    }

    public static class ParentBuilder extends Builder<ParentBuilder> {

        private List<Command> subcmd;

        private ParentBuilder() {
            this.subcmd = new ArrayList<>();
        }

        /**
         * Required. Adds a subcommand to the resulting parent command.
         *
         * @param cmd the {@link Command} to add
         * @return this builder
         */
        public ParentBuilder addCommand(Command cmd) {
            this.subcmd.add(cmd);
            return this;
        }

        @Override
        public Command build() {
            if (this.subcmd.size() < 1)
                throw new IllegalStateException("Parent command must have at least one subcommand");
            this.cmd.exec = new ParentExecutor().addCommands(this.subcmd);
            return super.build();
        }
    }

    public static class InstanceBuilder extends Builder<InstanceBuilder> {

        private InstanceBuilder() {
        }

        /**
         * Required. The executor of the command.
         *
         * @param exec the executor of the command
         * @return this Builder
         */
        public InstanceBuilder executor(CommandExecutor exec) {
            this.cmd.exec = exec;
            return this;
        }

    }

    // endregion

    /**
     * @return a {@link Command} builder
     */
    public static InstanceBuilder builder() {
        return new InstanceBuilder();
    }

    /**
     * @return a parent {@link Command} builder housing subcommands
     */
    public static ParentBuilder parent() {
        return new ParentBuilder();
    }

    private String syntax;
    private String[] names;
    private String shortDesc;
    private String description;
    private CommandExecutor exec;

    private Command() {
    }

    /**
     * @return the main name for this {@link Command}
     */
    public String getName() {
        return this.names[0];
    }

    /**
     * @return the names for this {@link Command}
     */
    public String[] getAliases() {
        return this.names;
    }

    /**
     * Returns the static syntax of this {@link Command}.
     *
     * <p>This should not contain the name of the command, as it can vary based on the alias used. As an example,
     * "/help" should be omitted from "/help 0."</p>
     *
     * <p>Should this return empty, it is assumed that the Command wants to use the syntax generated by the
     * {@link Arguments} object passed to its execution method instead.</p>
     *
     * @return the syntax of this command?
     */
    public Optional<String> getSyntax() {
        return Optional.ofNullable(this.syntax);
    }

    /**
     * @return the short description of this {@link Command}?
     */
    public Optional<String> getShortDescription() {
        return Optional.ofNullable(this.shortDesc);
    }

    /**
     * @return the description of this {@link Command}?
     */
    public Optional<String> getDescription() {
        return Optional.ofNullable(this.description);
    }

    /**
     * @return the {@link CommandExecutor} for this {@link Command}
     */
    public CommandExecutor getExec() {
        return this.exec;
    }

    /**
     * A case-insensitive check as to whether or not this {@link Command} has the given name.
     *
     * @param name the name to test
     * @return if this Command has a matching alias
     */
    public boolean hasName(String name) {
        for (String alias : this.names) {
            if (alias.equalsIgnoreCase(name)) return true;
        }

        return false;
    }
}
