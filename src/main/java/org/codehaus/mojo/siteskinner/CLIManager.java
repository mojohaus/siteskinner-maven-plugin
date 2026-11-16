package org.codehaus.mojo.siteskinner;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Subset of org.apache.maven.cli.CLIManager, which in part of Maven3.
 *
 * When migrating this plugin to M3, this class could be removed.
 *
 * @author Robert Scholte
 * @since 2.0.0
 */
public class CLIManager {
    public static final char SET_SYSTEM_PROPERTY = 'D';

    public static final char DEBUG = 'X';

    public static final char ACTIVATE_PROFILES = 'P';

    private Options options;

    public CLIManager() {
        options = new Options();
        options.addOption(Option.builder(String.valueOf(SET_SYSTEM_PROPERTY))
                .longOpt("define")
                .hasArg()
                .get());
        options.addOption(Option.builder(String.valueOf(DEBUG)).longOpt("debug").get());
        options.addOption(Option.builder(String.valueOf(ACTIVATE_PROFILES))
                .longOpt("activate-profiles")
                .hasArg()
                .get());
    }

    public CommandLine parse(String[] args) throws ParseException {
        // We need to eat any quotes surrounding arguments...
        String[] cleanArgs = splitAttachedValues(cleanArgs(args));

        CommandLineParser parser = new DefaultParser();

        return parser.parse(options, cleanArgs);
    }

    /**
     * Rewrites the value of an option that takes one into the {@code --long=value} form, as GnuParser read it:
     * {@code -Dfoo=bar}, {@code -D foo=bar} and {@code -P -a} become {@code --define=foo=bar} and
     * {@code --activate-profiles=-a}. DefaultParser reads {@code -Dfoo=bar} as the unknown option {@code -Dfoo}, and a
     * separate value starting with {@code -} as an option.
     */
    private String[] splitAttachedValues(String[] args) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            Option option = null;
            String value = null;
            if (arg.length() >= 2 && arg.charAt(0) == '-' && arg.charAt(1) != '-') {
                option = options.getOption(arg.substring(1, 2));
                value = arg.length() > 2 ? arg.substring(2) : null;
            } else if (arg.startsWith("--") && arg.indexOf('=') < 0) {
                option = options.getOption(arg.substring(2));
            }
            if (option != null && option.hasArg()) {
                if (value == null && i + 1 < args.length && !isOption(args[i + 1])) {
                    value = args[++i];
                }
                if (value != null) {
                    result.add("--" + option.getLongOpt() + "=" + value);
                    continue;
                }
            }
            result.add(arg);
        }
        return result.toArray(new String[0]);
    }

    private boolean isOption(String arg) {
        if (arg.startsWith("--")) {
            int eq = arg.indexOf('=');
            return options.hasLongOption(eq < 0 ? arg.substring(2) : arg.substring(2, eq));
        }
        return arg.length() >= 2 && arg.charAt(0) == '-' && options.hasShortOption(arg.substring(1, 2));
    }

    private String[] cleanArgs(String[] args) {
        List<String> cleaned = new ArrayList<>();

        StringBuilder currentArg = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            boolean addedToBuffer = false;

            if (arg.startsWith("\"")) {
                // if we're in the process of building up another arg, push it and start over.
                // this is for the case: "-Dfoo=bar "-Dfoo2=bar two" (note the first unterminated quote)
                if (currentArg != null) {
                    cleaned.add(currentArg.toString());
                }

                // start building an argument here.
                currentArg = new StringBuilder(arg.substring(1));
                addedToBuffer = true;
            }

            // this has to be a separate "if" statement, to capture the case of: "-Dfoo=bar"
            if (arg.endsWith("\"")) {
                String cleanArgPart = arg.substring(0, arg.length() - 1);

                // if we're building an argument, keep doing so.
                if (currentArg != null) {
                    // if this is the case of "-Dfoo=bar", then we need to adjust the buffer.
                    if (addedToBuffer) {
                        currentArg.setLength(currentArg.length() - 1);
                    }
                    // otherwise, we trim the trailing " and append to the buffer.
                    else {
                        // TODO: introducing a space here...not sure what else to do but collapse whitespace
                        currentArg.append(' ').append(cleanArgPart);
                    }

                    cleaned.add(currentArg.toString());
                } else {
                    cleaned.add(cleanArgPart);
                }

                currentArg = null;

                continue;
            }

            // if we haven't added this arg to the buffer, and we ARE building an argument
            // buffer, then append it with a preceding space...again, not sure what else to
            // do other than collapse whitespace.
            // NOTE: The case of a trailing quote is handled by nullifying the arg buffer.
            if (!addedToBuffer) {
                if (currentArg != null) {
                    currentArg.append(' ').append(arg);
                } else {
                    cleaned.add(arg);
                }
            }
        }

        if (currentArg != null) {
            cleaned.add(currentArg.toString());
        }

        int cleanedSz = cleaned.size();

        String[] cleanArgs = null;

        if (cleanedSz == 0) {
            cleanArgs = args;
        } else {
            cleanArgs = cleaned.toArray(new String[cleanedSz]);
        }

        return cleanArgs;
    }
}
