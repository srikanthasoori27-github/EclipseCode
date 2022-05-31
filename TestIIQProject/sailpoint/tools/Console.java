/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * A simple console interpreter for administration and testing.
 * This is normally subclassed by something that adds more commands.
 *
 */
public class Console {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    private static final Log log = LogFactory.getLog(Console.class);
    
    protected   String      _prompt     = "> ";
    private     boolean     _trace      = false;
    private     Runtime     _runtime    = Runtime.getRuntime();
    private     List	    _commands   = new ArrayList();
    private     List        _history    = new ArrayList();
    private     boolean     _stop       = false;

    @SuppressWarnings("unchecked")
    protected List<Command> getCommands() {
        return _commands;
    }

    /**
     * The name of the shell we spawn to handle external commands.
     * If this is null, external commands are disabled.
     */
    String _shell;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public Console() {
        addCommands();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Run
    //
    //////////////////////////////////////////////////////////////////////

    public void run(String args[]) throws GeneralException {

        String cmd = null;

        for (int i = 0 ; i < args.length ; i++) {

            if (args[i].equals("-c") && i+1 < args.length)
                cmd = args[i+1];

            else if (args[i].equals("-f") && i+1 < args.length) {
                // fake this up to be a -c source command
                cmd = "source " + args[i+1];
            }
        }

        if (cmd == null)
            interactiveConsole();
        else {
            PrintWriter stdout = getStdout();
            try {
                doCommand(cmd, stdout);
            } catch (Throwable th) {
                log.error(th);
                throw new GeneralException(th);
            }
        }
    }

    public static void println(Object p) {
        System.out.println(p);
    }

    public static void print(Object p) {
        System.out.print(p);
    }

    public void setPrompt(String p) {
        if (p == null)
            _prompt = "> ";
        else 
            _prompt = p + "> ";
    }

    /**
     * Implementations of this are used to add new commands which
     * are encapsulated in a different class.  SailPointConsole is
     * already way too long, so we need to start breaking it up
     * into multiple classes.
     */
    public interface ConsoleCommandExtension {
        /**
         * @return the top-level name used to invoke the command
         */
        String getCommandName();

        /**
         * @return the help text for the command
         */
        String getHelp();

        /**
         * This is called to execute the command
         * @param args the command arguments
         * @param opts formatting options
         * @param out the PrintWriter to write to
         * @throws GeneralException
         */
        void execute(List<String> args, FormattingOptions opts, PrintWriter out) throws GeneralException;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Command registration
    // Should be able to use introspection for some commands, but its
    // also nice to have a command-to-method map for commands that may
    // not have nice method names.
    //
    //////////////////////////////////////////////////////////////////////

    protected class Command {

        String fName;
        String fHelp;
        String fMethod;
        ConsoleCommandExtension extension;
        boolean fHidden;

        Command(String name, String help, String method) {
            fName   = name;
            fHelp   = help;
            fMethod     = method;
            extension = null;
        }

        Command(ConsoleCommandExtension extension) {
            if (extension != null) {
                fName = extension.getCommandName();
                fHelp = extension.getHelp();
                fMethod = null;
                this.extension = extension;
            }
        }

        public String getMethod() {
            return fMethod;
        }

        public String getName() {
            return fName;
        }

        public String getHelp() {
            return fHelp;
        }

        public void setHidden(boolean b) {
            fHidden = b;
        }

        public boolean isHidden() {
            return fHidden;
        }

        public ConsoleCommandExtension getExtension() {
            return extension;
        }

    }

    public void addCommand(String name, String help, String function) {

        Command c = new Command(name, help, function);
        _commands.add(c);
    }

    public void addHiddenCommand(String name, String help, String function) {

        Command c = new Command(name, help, function);
        c.setHidden(true);
        _commands.add(c);
    }

    public void addCommandExtension(ConsoleCommandExtension extension) {
        Command c = new Command(extension);
        _commands.add(c);
    }

    private void addCommands() {

        addCommand("Console Commands", null, null);

        addCommand("?", "display command help", "cmdHelp");
        addCommand("help", "display command help", "cmdHelp");
        addCommand("echo", "display a line of text", "cmdEcho");
        addCommand("quit", "quit the shell (same as exit)", "cmdQuit");
        addCommand("exit", "exit the shell (same as quit)", "cmdQuit");
        addCommand("source", "execute a file of commands", "cmdSource");
        addCommand("properties", "display system properties",
                "cmdProperties");
        addCommand("time", "show how much time a command takes to run.",
                "cmdTime");
        addCommand("xtimes", "Run a command x times.",
                "cmdXTimes");

    }

    /**
     * a PipeExecution is a simple structure to hold the name of the
     * pgoram to run, and its arguments
     */
    class PipeExecution {
        String program;
        List<String> args;

        PipeExecution(String program, List<String> args) {
            this.program = program;
            this.args = args;
        }

        public String getProgram() { return program;}
        public List<String> getArgs() { return args; }
    }

    public class FormattingOptions {
        private boolean noHeaderColumn;

        public boolean isNoHeaderColumn() {
            return noHeaderColumn;
        }

        public void setNoHeaderColumn(boolean noHeaderColumn) {
            this.noHeaderColumn = noHeaderColumn;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Command loop
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * Wrap System.out in a PrintWriter 
     * so that it may be used as the default writer 
     * if output redirection isn't being performed.
     */
    private PrintWriter getStdout() {
        return new PrintWriter(new OutputStreamWriter(System.out), true);
    }

    /**
     * Input buffer. Set only when running in interactive mode. Set to null
     * to signal termination
     */
    private BufferedReader _in;

    /**
     * Are we running in interactive mode?
     */
    protected boolean isInteractive() {

        return _in != null;
    }

    /**
     * Reads the next line of input
     */
    protected String readLine() {

        try {
            return _in.readLine();
        }
        catch (java.io.IOException e) {
            println(e);
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
            
            _in = null;
            return null;
        }
    }

    private void interactiveConsole() {

        _stop = false;

        _in = new BufferedReader(new InputStreamReader(System.in));

        PrintWriter stdout = getStdout();

        while (!_stop) {

            try {
                String line = readLine();
                if (line == null) {
                    // empty lines come in as "" so if this comes back null
                    // the stream is closed
                    _stop = true;
                }
                else {
                    // trim whitespace early so we don't add empty lines
                    // to the history   
                    line = line.trim();
                    if (line.length() > 0) {
                        // check for history substitution
                        boolean fromHistory = false;
                        if (line.charAt(0) == '!') {
                            String history = null;
                            if (line.length() > 1) {
                                String token = line.substring(1);
                                history = findHistory(token);
                            }
                            if (history == null)
                                printHistory();
                            else
                                println(history);
                            line = history;
                            fromHistory = true;
                        }

                        if (line != null) {
                            if (interactiveCommand(line, stdout)) {
                                // add to the history list if it was typed
                                // TODO: Would be nice to remove lines with 
                                // syntax errors
                                if (!fromHistory)
                                    addHistory(line);
                            }
                        }
                    }
                }
            }
            catch (Exception e) {
                println(e);
                if (log.isErrorEnabled())
                    log.error(e.getMessage(), e);
                
                _in = null;
            }
        }
    }

    private String findHistory(String token) {
        String line = null;
        if (_history != null) {
            int max = _history.size();
            if (token.equals("!")) {
                // !! on Unix means previous command
                if (max > 0)
                    line = (String)_history.get(max-1);
            }
            else {
                for (int i = max-1 ; i >= 0 ; i--) {
                    String history = (String)_history.get(i);
                    if (history.startsWith(token)) {
                        line = history;
                        break;
                    }
                }
            }
        }
        return line;
    }

    private void printHistory() {
        println("Command history:");
        if (_history != null) {
            for (int i = 0 ; i < _history.size() ; i++) {
                println(_history.get(i));
            }
        }
    }

    private void addHistory(String line) {
        // TODO: Should have a limit on this and start pruning
        if (_history == null) _history = new ArrayList();
        _history.add(line);
    }
    
    private boolean interactiveCommand(String line, PrintWriter out) {
        try {
            return doCommand(line, out);
        } catch (Throwable th) {
            //Swallow them all, doCommand logs its own errors and we don't want
            //the interactive console to quit.
        }
        return false;
    }

    private boolean doCommand(String line, PrintWriter out) throws GeneralException {

        boolean valid = false;

        // StringTokenizer unfortunately doesn't handle quoted strings,
        // so we have tokenizeLine.  This also isolates the output redirect
        // if any.

        List<String> args = new ArrayList<String>();
        FormattingOptions fOpts = new FormattingOptions();
        List<List<String>> pipes = new ArrayList<List<String>>();
        String redirect = tokenizeLine(line, args, pipes, fOpts);

        // locate the command

        String name = (args.size() > 0) ? args.get(0) : null;

        if (name != null && name.charAt(0) == '#')
            name = null;

        if (name != null) {

            // parse the command
            Pair<Command, Integer> commandSearchResult = findCommand(name);
            Command command = commandSearchResult.getFirst();
            int count = commandSearchResult.getSecond();

            // parse the pipes, if any
            List<PipeExecution> pipeExecs = new ArrayList<PipeExecution>();
            boolean parsedOk = parsePipeExecutions(pipes, pipeExecs);
            if (!parsedOk) {
                return false;
            }

            if (count > 1) {
                // could accept the first or last, but lets
                // make them be more specific
                showAmbiguity(name);
            }
            else {
                if (_trace) {
                    println(name);
                    for (int i = 1; i < args.size() ; i++)
                        print(" " + (String)args.get(i));
                    println("");
                }

                // out may be passed in, but can be changed with redirection
                PrintWriter redirectOut = null;
                if (redirect != null) {
                    // should we buffer this?
                    try {
                        String path = Util.findOutputFile(redirect);
                        FileOutputStream fos = new FileOutputStream(path);
                        redirectOut = new PrintWriter(new OutputStreamWriter(fos, "UTF-8"));
                    }
                    catch (Exception e) {
                        println(e);
                        if (log.isErrorEnabled())
                            log.error(e.getMessage(), e);
                        
                        redirectOut = null;
                        // could try to abort the command, but just
                        // let it continue to stdout
                    }
                }

                PrintWriter activeOut = out;
                if (redirectOut != null)
                    activeOut = redirectOut;

                try {
                    // if we didn't match a command, try to
                    // invoke a shell command?
                    if (command == null)
                        println("Unknown command '" + name + "'");
                    else {
                        // set this before we call the method since it may
                        // throw but we still want to keep it in the history?
                        valid = true;

                        // remove the initial name arg, so the it feels
                        // more like main(String[] args)
                        args.remove(0);

                        if (!Util.isEmpty(pipeExecs)) {

                            // we are piping, so don't want column headers
                            fOpts.setNoHeaderColumn(true);

                            // First, execute command and get its output
                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            PrintWriter cmdWriter = new PrintWriter(os);
                            executeCommand(command, args, fOpts, cmdWriter);
                            cmdWriter.flush();
                            String output = os.toString();

                            try {
                                // pass it through the pipes, one at a time
                                for (int i = 0; i < pipeExecs.size(); i++) {
                                    PipeExecution pipeExec = pipeExecs.get(i);

                                    InputStream inputStream = new ByteArrayInputStream(output.getBytes());
                                    if (i == pipeExecs.size() - 1) {
                                        // the last pipe, so write to activeOut
                                        executePipe(pipeExec.getProgram(), pipeExec.getArgs(), inputStream, activeOut);
                                        activeOut.flush();
                                    } else {
                                        // a middle pipe
                                        ByteArrayOutputStream middleOut = new ByteArrayOutputStream();
                                        PrintWriter pw = new PrintWriter(middleOut);
                                        executePipe(pipeExec.program, pipeExec.getArgs(), inputStream, pw);
                                        pw.flush();
                                        output = middleOut.toString();
                                    }
                                }
                            }
                            catch (Exception e) {
                                println(e);
                                throw new GeneralException(e);
                            }
                        }
                        else {
                            // No pipe was requested
                            executeCommand(command, args, fOpts, activeOut);
                        }
                    }
                }	
                catch (Throwable e) {
                    // should this be sent to the output stream?
                    println(e);
                    if (log.isErrorEnabled())
                        log.error(e.getMessage(), e);
                    
                    throw new GeneralException(e);
                } finally {

                    // close the redirection stream if we had one
                    if (redirectOut != null)
                        redirectOut.close();
    
                    if ( command != null &&
                         ( command.getName().equals("quit") ||
                           command.getName().equals("exit") ) )
                        _stop = true;
                }
            }
        }

        return valid;
    }

    /**
     * Populate the list of PipeExecution objects from the raw pipe strings previously
     * from the command.
     * @param pipes the inout list of list of string parsed from the pipes found in command line
     * @param pipeExecs the lsit of PipeExecution to populate
     * @return true if successfully parsed, otherwise false
     */
     private boolean parsePipeExecutions(List<List<String>> pipes, List<PipeExecution> pipeExecs) {
        // parse the pipes, if any
        for(List<String> pipeInfo : pipes) {
            String program = pipeInfo.get(0);
            List<String> pipeArgs = new ArrayList<String>();
            for (int i = 1; i < pipeInfo.size(); i++) {
                pipeArgs.add(pipeInfo.get(i));
            }
            PipeExecution pipeExec = new PipeExecution(program, pipeArgs);
            pipeExecs.add(pipeExec);
        }
        return true;
    }

    protected void executeCommand(Command command, List<String> args, FormattingOptions formattingOptions, PrintWriter activeOut)
        throws Throwable {

        preCommand(command.getName(), args, activeOut);
        if (command.getExtension() != null) {
            command.getExtension().execute(args, formattingOptions, activeOut);
        }
        else {
            callMethod(command.getMethod(), args, formattingOptions, activeOut);
        }
        postCommand(command.getName(), args, activeOut);
    }

    /**
     * Invoke the given program using the java ProcessBuilder, supplying it
     * with the given InputStream to read, and PrintWriter to write to
     * @param program the program to invoke
     * @param args the args to pass to program when invoked
     * @param in the InputStream to be read by the prooram
     * @param out the PrintWriter the program should write to
     * @throws Throwable
     */
    protected void executePipe(String program, List<String> args, InputStream in, PrintWriter out)
            throws Throwable {

        List<String> command = new ArrayList<String>();
        command.add(program);
        command.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(command);

        // the process' stdout and stderr will appear together as stdout
        pb.redirectErrorStream(true);

        // this will throw an IOException if not a command
        // recognized by teh operating system
        Process p = pb.start();

        OutputStream os = p.getOutputStream();
        if (os != null) {
            // route our InputStream 'in' into the process's input
            IOUtils.copy(in, os);
            try {
                os.flush();
                os.close();
            }
            catch (IOException e) {
                log.warn("The process '" + program + "' closed or ignored its given input stream");
            }
        }
        else {
            log.warn("The process '" + program + "' ignored its given input stream");
        }

        // route the process's output into our PrintWriter 'out'
        IOUtils.copy(p.getInputStream(), out);
        out.flush();
    }
    
    protected Pair<Command, Integer> findCommand(String name) {

        Command command = null;
        int count = 0;

        int max = _commands.size();
        for (int i = 0; i < max; i++) {

            Command c = (Command) _commands.get(i);

            // division text has a name but no method or extension
            if ( (c.getMethod() != null || c.getExtension() != null) && c.getName().indexOf(name) == 0) {

                count++;
                if (command == null)
                    command = c;

                // If the entered name matched the complete command name
                // exactly, then terminate the loop without looking
                // for ambiguities. There may be other commands with
                // the same prefix, but we match the shortest one.

                if (c.getName().length() == name.length()) {
                    command = c;
                    count = 1;
                    break;
                }
            }
        }

        return new Pair<Command, Integer>(command, count);
    }


    /**
     * A hook for the subclass to insert metering or something 
     * around the command invocation.
     */
    public void preCommand(String name, List<String> args, PrintWriter out) {
    }

    public void postCommand(String name, List<String> args, PrintWriter out) {
    }

    /**
     * Print a notice that the command could not be resolved
     * unambiguously
     * @param name the command name
     */
    private void showAmbiguity(String name) {

        println("The command '" + name + "' is ambiguous." +
                "  Possible matches are:");

        int max = _commands.size();
        for (int i = 0 ; i < max ; i++) {

            Command c = (Command)_commands.get(i);
            if ( (c.getMethod() != null || c.getExtension() != null) &&
                    c.getName().indexOf(name) == 0) {

                println("  " + c.getName());
            }
        }
    }

    private String tokenizeLine(String line, List<String> args, List<List<String>> pipes, FormattingOptions fOpts) {

        boolean captureRedirect = false;
        boolean capturePipe = false;
        String redirectFile = null;
        List<String> currentPipe = null;
        int length = line.length();
        char c = 0;


        for (int i = 0 ; i < length ; i++) {

            // trim leading space
            while (i < length && Character.isSpaceChar(line.charAt(i))) i++;

            // check for leading quote
            char quote = 0;
            if (i < length) {
                c = line.charAt(i);
                if (c == '\'' || c == '"') {
                    quote = c;
                    i++;
                }
            }

            // find the end of the token
            int start = i;
            while (i < length) {

                c = line.charAt(i);
                if (c == '\\') {
                    // to handle this properly, we would need
                    // to remove the escape char from the resulting
                    // token, will have to use a string buffer or something
                    // rather than String.substring below.
                    i++;
                }
                else if (c == quote)
                    break;
                else if (quote == 0 && Character.isSpaceChar(c))
                    break;

                i++;
            }

            // extract the token
            int end = i;
            if (quote != 0 && c == quote && end > start) end--;
            String token = line.substring(start, i);

            if (captureRedirect) {
                // this token is the output redirection file
                redirectFile = token;
            }
            else if (token.equals(">")) {
                // next token is output redirection
                captureRedirect = true;
                if (capturePipe) {
                    if (!Util.isEmpty(currentPipe)) {
                        pipes.add(currentPipe);
                    }
                    capturePipe = false;
                    currentPipe = null;
                }
            }
            else if (token.equals("|")) {
                if (capturePipe) {
                    if (!Util.isEmpty(currentPipe)) {
                        pipes.add(currentPipe);
                    }
                }
                currentPipe = new ArrayList<String>();
                capturePipe = true;
            }
            else if (capturePipe) {
                currentPipe.add(token);
            }
            else {
                // store the argument
                args.add(token);
            }

            if (i >= (length - 1)) {
                // special handling of last token
                if (!Util.isEmpty(currentPipe)) {
                    pipes.add(currentPipe);
                }
            }
        }

        // special formatting options will be stored into FormattingOptions
        // object, and removed from args
        if (args.contains("--bare")) {
            fOpts.setNoHeaderColumn(true);
            args.remove("--bare");
        }

        return redirectFile;
    }

    /**
     * Invoke the named command.
     *
     * It will now look for one of two signatures:
     *     (List, FormattingOptions, PrintWriter)
     * or
     *     (List, PrintWriter)
     * @param name the name of the command to invoke
     * @param args the arguments to pass to pipe invocation
     * @param fOpts the FormattingOptions to pass to the command
     * @param out the PrintWriter for the pipe to write to
     * @throws Throwable if the pipe cannot be found, or fails to execute
     */

    private void callMethod(String name, List<String> args, FormattingOptions fOpts, PrintWriter out)
    throws Throwable {

        // now that we have the two classes to deal with, this
        // might be a lot slower?

        // First, try to use the new command method that support FormattingOptions parameter

        Method m = getFormattingCommandMethodInHierarchy(name);
        if (m != null) {
            // If the method throws an exception, it gets wrapped in
            // an InvocationTargetException exception.  Unwrap it and
            // propagate it.

            try {
                m.invoke(this, new Object[] {args, fOpts, out});
            }
            catch (InvocationTargetException e) {
                Throwable target = e.getTargetException();
                throw target;
            }
        }
        else {
            // Did't find new signature, so let's fall back to old signature, and
            // we can't pass down formatting options

            m = getCommandMethodInHierarchy(name);
            if (m == null) {
                throw new IllegalStateException("Method: " + name + " is not present in hierarchy");
            }
            else {
                // If the method throws an exception, it gets wrapped in
                // an InvocationTargetException exception.  Unwrap it and
                // propagate it.

                try {
                    m.invoke(this, new Object[] {args, out});
                }
                catch (InvocationTargetException e) {
                    Throwable target = e.getTargetException();
                    throw target;
                }
            }
        }
    }

    // looks up the command Method (without FormattingOptions in signature)
    // in the entire chain as opposed to just two classes previously
    private Method getCommandMethodInHierarchy(String name)
        throws Throwable {
        
        Method m = null;
        Class<?> currentClass = this.getClass();
        do {
            m = getCommandMethodInClass(currentClass, name);
            if (m != null) {
                return m;
            }
            currentClass = currentClass.getSuperclass();
        } while (currentClass != null);

        return m;
    }

    private Method getCommandMethodInClass(Class<?> clazz, String name)
        throws Throwable {
        
        try {
            return clazz.getDeclaredMethod(name, List.class, PrintWriter.class);
        }
        catch (Exception e) {
            return null;
        }
    }

    // looks up the command Method (with FormattingOptions in signature)
    // in the entire chain as opposed to just two classes previously
    private Method getFormattingCommandMethodInHierarchy(String name)
            throws Throwable {

        Method m = null;
        Class<?> currentClass = this.getClass();
        do {
            m = getFormattingCommandMethodInClass(currentClass, name);
            if (m != null) {
                return m;
            }
            currentClass = currentClass.getSuperclass();
        } while (currentClass != null);

        return m;
    }

    /**
     * Look for a method in the the given Class with the given name
     * and with signature (List, FormattingOptions, PrintWriter)
     * @param clazz the class to inspect
     * @param name the name of the method to find
     * @return the matching Method, or null
     * @throws Throwable
     */
    private Method getFormattingCommandMethodInClass(Class<?> clazz, String name)
            throws Throwable {

        try {
            return clazz.getDeclaredMethod(name, List.class, FormattingOptions.class, PrintWriter.class);
        }
        catch (Exception e) {
            return null;
        }
    }

    /***********************************************************************/
    //
    // Misc utilities
    //
    /***********************************************************************/

    private StringBuffer stringField(String s, int width) {

        StringBuffer field = new StringBuffer();

        field.append(s);
        int pad = width - s.length();
        if (pad > 0) {
            for (int i = 0 ; i < pad ; i++)
                field.append(' ');
        }

        return field;
    }

    /***********************************************************************/
    //
    // Command handlers
    //
    /***********************************************************************/

    private void cmdHelp(List<String> args, PrintWriter out)
        throws Exception {

        int max = _commands.size();
        for (int i = 0 ; i < max ; i++) {

            Command c = (Command)_commands.get(i);
            if (!c.isHidden()) {
                if (c.getMethod() == null && c.getExtension() == null) {
                    // this is section division text
                    out.println("");
                    out.println(c.getName());
                    out.println("");
                }
                else {
                    StringBuffer f = stringField(c.getName(), 20);
                    out.println(f + " " + c.getHelp());
                }
            }
        }
        out.println("");

    }

    private void cmdEcho(List<String> args, PrintWriter out)
        throws Exception {

        for (String arg : Util.iterate(args)) {
            out.print(arg);
            out.print(" ");
        }
        out.println("");
    }

    private void cmdSource(List<String> args, PrintWriter out) throws GeneralException {

        if (args.size() < 1)
            println("source <filename>");
        else {
            _stop = false;
            BufferedReader in = null;
            try {
                String file = Util.findFile(args.get(0));
                FileReader fr = new FileReader(file);
                in = new BufferedReader(fr);

                String line;
                while((line = in.readLine()) != null && !_stop) {

                    doCommand(line, out);
                }
            }
            catch (Exception e) {
                println(e);
                if (log.isErrorEnabled())
                    log.error(e.getMessage(), e);
                    
                throw new GeneralException(e);
            } finally {
                try {
                    if (in != null) in.close();
                } catch (Throwable th) {
                    throw new GeneralException(th);
                }
            }
        }
    }

    private void cmdQuit(List<String> args, PrintWriter out)
        throws Exception {
    }


    private void cmdProperties(List<String> args, PrintWriter out)
        throws Exception {

        Util.dumpProperties(out);
    }

    private void cmdTime(List<String> args, PrintWriter out)
        throws Throwable {
        
        if (args.size() == 0) {
            out.println("Usage: time other command");
            return;
        }
        
        long start = System.currentTimeMillis();
        out.println("Started at: " + new Date(start));

        String commandName = args.get(0);
        Pair<Command, Integer> result = findCommand(commandName);
        Command command = result.getFirst();
        int count = result.getSecond();
        if (count > 1) {
            showAmbiguity(commandName);
        } else {
            if (command == null) {
                out.println("Unknown command: '" + commandName + "'");
            } else {
                // can not just remove from args because time may 
                // be called with xtimes
                List<String> newArgs = new ArrayList<String>();
                for (int i=1; i<args.size(); ++i) {
                    newArgs.add(args.get(i));
                }
                executeCommand(command, newArgs, null, out);
            }
        }
        
        long end = System.currentTimeMillis();
        out.println("Time taken: " + (end-start) + " ms");
        out.println();
    }
    
    private void cmdXTimes(List<String> args, PrintWriter out) 
        throws Throwable {
        
        if (args.size() < 2) {
            out.println("Usage: xtimes x other command");
            return;
        }

        int x;
        try {
            x = Integer.parseInt(args.get(0));
        } catch(NumberFormatException ex) {
            out.println("Invalid number: " + args.get(0));
            return;
        } 
        
        if (x < 1) {
            out.println("x must be greater than or equal to 1");
            return;
        }
        
        String commandName = args.get(1);
        Pair<Command, Integer> result = findCommand(commandName);
        Command command = result.getFirst();
        int count = result.getSecond();
        if (count > 1) {
            showAmbiguity(commandName);
        } else {
            if (command == null) {
                out.println("Unknown command: '" + commandName + "'");
            } else {
                args.remove(0);
                args.remove(0);
                
                for (int i=0; i<x; ++i) {
                    executeCommand(command, args, null, out);
                }
            }
        }
    }
}
