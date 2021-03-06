package com.github.xemiru.general.misc;

import com.github.xemiru.general.Arguments;
import com.github.xemiru.general.Command;
import com.github.xemiru.general.CommandContext;
import com.github.xemiru.general.RawArguments;
import com.github.xemiru.general.exception.CommandException;

import java.util.Comparator;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Object managing messages generated by help commands.
 */
public interface HelpGenerator {

    /**
     * Databag class for information useful to a help command.
     */
    class HelpInfo {

        /**
         * Lists a {@link Command}'s aliases in string form.
         *
         * <p>This does not include the command's main name, stored in the 0th index of the aliases array.</p>
         *
         * @param cmd the Command to harvest aliases from
         * @return a String form of a list of the command's aliases, or an empty string if it has none
         */
        private static String aliases(Command cmd) {
            if (cmd.getAliases().length <= 1) return null;

            String[] list = new String[cmd.getAliases().length - 1];
            System.arraycopy(cmd.getAliases(), 1, list, 0, cmd.getAliases().length - 1);

            StringBuilder sb = new StringBuilder(list[0]);
            for (int i = 1; i < list.length; i++) sb.append(", ").append(list[i]);
            return sb.toString();
        }

        private String name;
        private String syntax;
        private String aliases;
        private String description;
        private Command cmd;

        /**
         * Collects help information for the {@link Command} stored in the given {@link CommandContext}.
         *
         * <p>The command is dry-ran during the runtime of the constructor. It is possible that the command will throw
         * an exception; one should handle this scenario accordingly.</p>
         *
         * @param ctx the context containing the command
         */
        public HelpInfo(CommandContext ctx) {

            this.cmd = ctx.getCommand();
            if (this.cmd.getSyntax().isPresent()) this.syntax = this.cmd.getSyntax().get();
            else {
                Arguments simArgs = new Arguments(ctx, new RawArguments(new String[0]));
                ctx.setDry(true).execute(simArgs);
                this.syntax = simArgs.getSyntax();
            }

            this.name = this.cmd.getName();
            this.syntax = this.cmd.getName() + " " + this.syntax;
            this.aliases = HelpInfo.aliases(this.cmd);
            this.description = this.cmd.getDescription().orElse(this.cmd.getShortDescription().orElse(null));

        }

        /**
         * @return the source {@link Command} of this {@link HelpInfo}
         */
        public Command getCommand() {
            return this.cmd;
        }

        /**
         * @return the main name of the source {@link Command}.
         */
        public String getName() {
            return this.name;
        }

        /**
         * @return the syntax of the source {@link Command}
         */
        public String getSyntax() {
            return this.syntax;
        }

        /**
         * @return the aliases of the source {@link Command}?
         */
        public Optional<String> getAliases() {
            return Optional.ofNullable(this.aliases);
        }

        /**
         * Returns the description of the source {@link Command}.
         *
         * <p>If the command had no full description, this will instead return its short description. If the command did
         * not have a short description either, this returns an empty Optional.</p>
         *
         * @return the description of the source {@link Command}?
         */
        public Optional<String> getDescription() {
            return Optional.ofNullable(this.description);
        }

    }

    /**
     * Returns the amount of items to be shown in a page.
     *
     * <p>Use 0 or a negative number to disable pagination for calls to
     * {@link #sendHelp(CommandContext, TreeMap, int, int)}. The method will always receive 0 as the requested and max
     * page if this is disabled.</p>
     *
     * <p>The value can be adapted based on the provided context.</p>
     *
     * @param context the context executing the help command
     * @return the amount of items to be shown in a help page
     */
    int getPageSize(CommandContext context);

    /**
     * Returns the sorting method used to organize commands prior to pagination.
     *
     * <p>The comparator can be adapted based on the provided context.</p>
     *
     * @param context the context executing the help command
     * @return the comparator used to sort entries when providing commands with short descriptions (a call to
     *     {@link #sendHelp(CommandContext, TreeMap, int, int)}
     */
    Comparator<String> getSorter(CommandContext context);

    /**
     * Sends help text.
     *
     * <p>Items within the passed map are already the product of pagination; one does not need the {@code page}
     * parameter to perform it themselves. The {@code page} and {@code maxPage} parameters exists purely to be able to
     * relay such information back to the user.</p>
     *
     * <p>It is possible to receive an empty map if the user attempts to provide a page out of bounds.</p>
     *
     * @param context the context executing the help command
     * @param help the mapping of command names to short descriptions
     * @param page the requested page
     * @param maxPage the max page
     */
    void sendHelp(CommandContext context, TreeMap<String, Optional<String>> help, int page, int maxPage);

    /**
     * Sends help text for a specific command, held by the provided {@link HelpInfo} object.
     *
     * @param context the context executing the help command
     * @param info the HelpInfo object for the requested command
     */
    void sendFullHelp(CommandContext context, HelpInfo info);

    /**
     * Creates a {@link CommandException} to be thrown when the queried command crashes during information harvesting.
     *
     * @param ctx the context executing the help command
     * @param input the input matching the crashing command
     * @return a CommandException to be thrown
     */
    CommandException createError(CommandContext ctx, String input);

    /**
     * Creates a {@link CommandException} to be thrown when provided with an unknown command.
     *
     * @param ctx the context executing the help command
     * @param input the input deemed as an unknown command reference
     * @return a CommandException to be thrown
     */
    CommandException createErrorUnknown(CommandContext ctx, String input);

}
