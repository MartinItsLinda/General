package com.github.xemiru.general;

import com.github.xemiru.general.exception.ParseException;
import com.github.xemiru.general.exception.SyntaxException;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Acts as a stack for command parameters, enabling advanced parameter handling.
 */
public class Arguments {

    private static class Error {

        String message;
        Throwable exception;

        Error(String message) {
            this.message = message;
            this.exception = null;
        }

        Error(Throwable exception) {
            this.exception = exception;
        }

    }

    private static class Parameter {

        String token;
        Object value;

        Parameter(String token, Object value) {
            this.token = token;
            this.value = value;
        }

    }

    private Error error;
    private int current;
    private List<ArgumentParser<?>> syntax;
    private List<Parameter> parsed;
    private RawArguments rawArgs;
    private CommandContext context;

    public Arguments(CommandContext context, RawArguments args) {
        this.error = null;
        this.current = 0;
        this.context = context;
        this.syntax = new ArrayList<>();
        this.parsed = new ArrayList<>();
        this.rawArgs = requireNonNull(args);
    }

    /**
     * @return a deep copy of this {@link Arguments} object
     */
    public Arguments copy() {
        Arguments copy = new Arguments(this.context, this.rawArgs.copy());
        copy.error = this.error;
        copy.current = this.current;
        copy.syntax = this.syntax;
        copy.parsed = this.parsed;

        return copy;
    }

    /**
     * @return the {@link CommandContext} owning this {@link Arguments} object
     */
    public CommandContext getContext() {
        return this.context;
    }

    /**
     * Sets the new {@link CommandContext} of this {@link Arguments} object.
     *
     * <p>The new context cannot be null.</p>
     *
     * @param newContext the new context
     * @return this Arguments
     */
    public Arguments setContext(CommandContext newContext) {
        this.context = requireNonNull(newContext);
        return this;
    }

    /**
     * @return the syntax generated by this {@link Arguments} object as a result of calls to the write methods
     */
    public String getSyntax() {
        StringBuilder sb = new StringBuilder();
        for (ArgumentParser<?> parser : this.syntax) sb.append(' ').append(getSyntax(parser));
        return sb.toString().trim();
    }

    /**
     * Suggests completion for the last written raw argument.
     *
     * <p>The list may return empty if there are no suggested completions.</p>
     *
     * @return a List of suggestions to finish the last written argument (can be empty)
     */
    public List<String> complete() {
        String[] raw = this.rawArgs.getRaw();
        if(raw.length <= 0) return new ArrayList<>();
        String last = raw[raw.length - 1];

        Set<String> suggested;
        ArgumentParser<?> parser = this.syntax.get(raw.length - 1);
        try {
            suggested = parser.getSuggestions();
        } catch (Throwable e) {
            throw new ParseException(String.format("Parser %s with typename %s crashed while generating suggestions",
                parser.getClass().getName(), parser.getTypename()));
        }

        if (suggested == null) return new ArrayList<>(); // return empty

        Set<String> selection = new LinkedHashSet<>(suggested);
        selection.removeIf(it -> !it.startsWith(last));
        List<String> completion = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        selection.forEach(sel -> {
            if (sel.contains(" ")) { // if we have whitespace, we need to quote it
                if (sel.contains("\"") && sel.contains("\'")) {
                    // doesn't matter which we use, so we'll use " and escape any quotes already in there
                    sb.append('"')
                        .append(sel.replaceAll(Pattern.quote("\""), Matcher.quoteReplacement("\\\"")))
                        .append('"');
                } else if (sel.contains("\"")) {
                    // use single quotes to avoid having to escape doubles
                    sb.append('\'').append(sel).append('\'');
                } else {
                    // use double quotes to avoid having to escape singles
                    sb.append('"').append(sel).append('"');
                }

                completion.add(sb.toString());
                sb.setLength(0);
            } else completion.add(sel);
        });

        return completion;
    }

    /**
     * Returns the next parameter that was successfully parsed.
     *
     * <p>The order of the parameters is set by the order of the calls to the write methods. Each call to a write method
     * will add to the parameters held by this {@link Arguments} object.</p>
     *
     * <p>Any syntax errors withheld by calls the write methods will be thrown when calling this method -- this allows
     * the syntax string to be generated.</p>
     *
     * <p>If there are no more parameters, this method throws an {@link IllegalStateException}.</p>
     *
     * <p>If the context denotes that the command using these Arguments is being dry-ran, this method throws an
     * {@link IllegalStateException}.</p>
     *
     * @param <T> the type of the parameter
     * @return the next parameter successfully parsed
     *
     * @throws ClassCastException if the requested parameter type did not match the actual
     * @throws IllegalStateException if no more parameters are held by this Arguments object
     * @throws SyntaxException if syntax errors had occured during argument parsing
     */
    public <T> T next() {
        if (this.context.isDry()) throw new IllegalStateException("Cannot request parameters during dry-run");

        if (this.isErrored()) {
            if (this.error.exception != null) throw new RuntimeException(this.error.exception);
            String syn = this.context.getLabel() + " " + this.getSyntax();
            if (this.context.getCommand() != null && this.context.getCommand().getSyntax().isPresent()) {
                syn = this.context.getCommand().getSyntax().get();
            }

            throw new SyntaxException(syn, this.error.message);
        }

        if (this.parsed.isEmpty()) throw new IllegalStateException("No more parameters");
        return (T) this.parsed.get(this.current++).value;
    }

    /**
     * Returns the next parameter that was successfully parsed.
     *
     * <p>The order of the parameters is set by the order of the calls to the write methods. Each call to a write method
     * will add to the parameters held by this {@link Arguments} object.</p>
     *
     * <p>The fallback value is returned in a case where a {@link SyntaxException} would otherwise be thrown.</p>
     *
     * <p>If there are no more parameters, this method throws an {@link IllegalStateException}.</p>
     *
     * <p>If the context denotes that the command using these Arguments is being dry-ran, this method throws an
     * {@link IllegalStateException}.</p>
     *
     * @param <T> the type of the parameter
     * @param fallback the fallback value to use in the case of a SyntaxException
     * @return the next parameter successfully parsed
     *
     * @throws ClassCastException if the requested parameter type did not match the actual
     * @throws IllegalStateException if no more parameters are held by this Arguments object
     */
    public <T> T next(T fallback) {
        try {
            return this.next();
        } catch (SyntaxException e) {
            return fallback;
        }
    }

    /**
     * Drops the earliest {@code n} parameters in this {@link Arguments} object's parsing history.
     *
     * <p>Use with care. An {@link ArrayIndexOutOfBoundsException} can be thrown if not enough parsers were written, or
     * if parser handling had errored with the latter only showing having occured through a call to {@link #next()}.</p>
     *
     * <p>This will also drop corresponding syntax.</p>
     *
     * @param n the amount of parameters to drop
     * @return this Arguments
     */
    public Arguments drop(int n) {
        this.rawArgs.drop(n);
        for (int i = 0; i < n; i++) {
            this.parsed.remove(0);
            this.syntax.remove(0);
            this.current--;
        }

        return this;
    }

    /**
     * @return the {@link RawArguments} used by this {@link Arguments} object
     */
    public RawArguments getRaw() {
        return this.rawArgs;
    }

    // region write overloads

    /**
     * Writes a parameter to this set of arguments using the provided {@link ArgumentParser}.
     *
     * <p>The typename of the provided parser is renamed.</p>
     *
     * @param typename the typename to identify with
     * @param parser the parser to use
     * @param <T> the type of the resulting parameter
     * @return this Arguments
     */
    public <T> Arguments named(String typename, ArgumentParser<T> parser) {
        return this.named(typename, parser, null);
    }

    /**
     * Writes a parameter to this set of arguments using the provided {@link ArgumentParser} and checks it using the
     * given {@link Predicate}.
     *
     * <p>The typename of the provided parser is renamed.</p>
     *
     * <p>The default error message for a parameter failing the Predicate's check is "invalid value." This can be
     * changed using {@link #write(ArgumentParser, Predicate, String)}.</p>
     *
     * @param typename the typename to identify with
     * @param parser the parser converting the raw argument into a usable parameter
     * @param check the predicate checking whether the resulting parameter is usable
     * @param <T> the type of the resulting parameter
     * @return this Arguments
     */
    public <T> Arguments named(String typename, ArgumentParser<T> parser, Predicate<T> check) {
        return this.named(typename, parser, check, null);
    }

    /**
     * Writes a parameter to this set of arguments using the provided {@link ArgumentParser} and checks it using the
     * given {@link Predicate}.
     *
     * <p>The typename of the provided parser is renamed.</p>
     *
     * <p>Note that the error message is always appended with the string that the passed ArgumentParser deemed invalid.
     * Specifically, the message becomes "message: string".</p>
     *
     * @param typename the typename to identify with
     * @param parser the parser converting the raw argument into a usable parameter
     * @param check the predicate checking whether the resulting parameter is usable
     * @param errorMsg the error message sent when the predicate fails to verify the resulting parameter
     * @param <T> the type of the resulting parameter
     * @return this Arguments
     */
    public <T> Arguments named(String typename, ArgumentParser<T> parser, Predicate<T> check, String errorMsg) {
        return this.write(ArgumentParsers.rename(parser, typename), check, errorMsg);
    }

    /**
     * Writes a parameter to this set of arguments using the provided {@link ArgumentParser}.
     *
     * @param parser the parser converting the raw argument into a usable parameter
     * @param <T> the type of the resulting parameter
     * @return this Arguments
     */
    public <T> Arguments write(ArgumentParser<T> parser) {
        return this.write(parser, null);
    }

    /**
     * Writes a parameter to this set of arguments using the provided {@link ArgumentParser} and checks it using the
     * given {@link Predicate}.
     *
     * <p>The default error message for a parameter failing the Predicate's check is "invalid value." This can be
     * changed using {@link #write(ArgumentParser, Predicate, String)}.</p>
     *
     * @param parser the parser converting the raw argument into a usable parameter
     * @param check the predicate checking whether the resulting parameter is usable
     * @param <T> the type of the resulting parameter
     * @return this Arguments
     */
    public <T> Arguments write(ArgumentParser<T> parser, Predicate<T> check) {
        return this.write(parser, check, null);
    }

    // endregion

    /**
     * Writes a parameter to this set of arguments using the provided {@link ArgumentParser} and checks it using the
     * given {@link Predicate}.
     *
     * <p>Note that the error message is always appended with the string that the passed ArgumentParser deemed invalid.
     * Specifically, the message becomes "message: string".</p>
     *
     * @param parser the parser converting the raw argument into a usable parameter
     * @param check the predicate checking whether the resulting parameter is usable
     * @param errorMsg the error message sent when the predicate fails to verify the resulting parameter
     * @param <T> the type of the resulting parameter
     * @return this Arguments
     */
    public <T> Arguments write(ArgumentParser<T> parser, Predicate<T> check, String errorMsg) {
        this.syntax.add(parser);
        if (this.isErrored()) return this; // pointless to do anything else if we've already errored

        Optional<String> def = parser.getDefaultToken();
        Optional<T> defVal = parser.getDefaultParameter();
        Optional<String> firstArg = this.rawArgs.peek();
        if (!def.isPresent() && !defVal.isPresent() && !firstArg.isPresent()) {
            this.error = new Error("expected <" + parser.getTypename() + ">; got nothing");
        } else {
            RawArguments args = this.rawArgs;
            if (!firstArg.isPresent()) {
                if (defVal.isPresent()) {
                    this.parsed.add(new Parameter("", defVal.get()));
                    return this;
                } else args = new RawArguments(new String[]{def.get()});
            }

            int start = args.getNextIndex();
            try {
                T value = parser.parse(args);
                String parsed = args.getParsed(start);
                if (value == null) {
                    this.error = new Error(new ParseException(String.format("Parser %s with typename %s produced null value",
                        parser.getClass().getName(), parser.getTypename())));
                } else if (check != null && !check.test(value)) {
                    this.error = new Error(errorMsg == null ?
                        "invalid value: " + parsed :
                        errorMsg + ": " + parsed);
                } else {
                    this.parsed.add(new Parameter(parsed, value));
                }
            } catch (ParseException e) {
                this.error = new Error(e.getMessage());
            } catch (Throwable e) {
                this.error = new Error(e);
            }
        }

        return this;
    }

    /**
     * Internal method.
     *
     * @param parser the {@link ArgumentParser} to generate syntax for
     * @return the Syntax string of the provided ArgumentParser
     */
    private String getSyntax(ArgumentParser<?> parser) {
        StringBuilder sb = new StringBuilder();
        if (!parser.getDefaultToken().isPresent() && !parser.getDefaultParameter().isPresent())
            sb.append('<').append(parser.getTypename()).append('>');
        else {
            sb.append('[').append(parser.getTypename());
            if (!parser.getDefaultParameter().isPresent())
                parser.getDefaultToken().ifPresent(it -> sb.append('=').append(it));
            sb.append(']');
        }

        return sb.toString();
    }

    /**
     * Internal method.
     *
     * @return if argument parsing had encountered an error
     */
    private boolean isErrored() {
        return this.error != null;
    }
}
