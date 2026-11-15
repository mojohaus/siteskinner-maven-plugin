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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CLIManagerTest {

    private static CommandLine parse(String arguments) throws Exception {
        return new CLIManager().parse(CommandLineUtils.translateCommandline(arguments));
    }

    @Test
    void attachedSystemProperties() throws Exception {
        CommandLine cl = parse("-Dfoo=bar -Dflag");
        assertArrayEquals(new String[] {"foo=bar", "flag"}, cl.getOptionValues(CLIManager.SET_SYSTEM_PROPERTY));
    }

    @Test
    void separatedAndLongSystemProperties() throws Exception {
        CommandLine cl = parse("-D foo=bar --define baz=qux");
        assertArrayEquals(new String[] {"foo=bar", "baz=qux"}, cl.getOptionValues(CLIManager.SET_SYSTEM_PROPERTY));
    }

    @Test
    void quotedSystemPropertyKeepsSpaces() throws Exception {
        CommandLine cl = parse("\"-Dfoo=bar baz\"");
        assertArrayEquals(new String[] {"foo=bar baz"}, cl.getOptionValues(CLIManager.SET_SYSTEM_PROPERTY));
    }

    @Test
    void debugAndProfiles() throws Exception {
        CommandLine cl = parse("-X -P a,!b --activate-profiles c");
        assertTrue(cl.hasOption(CLIManager.DEBUG));
        assertArrayEquals(new String[] {"a,!b", "c"}, cl.getOptionValues(CLIManager.ACTIVATE_PROFILES));
    }

    @Test
    void attachedProfiles() throws Exception {
        CommandLine cl = parse("-Pa,b");
        assertFalse(cl.hasOption(CLIManager.DEBUG));
        assertArrayEquals(new String[] {"a,b"}, cl.getOptionValues(CLIManager.ACTIVATE_PROFILES));
    }

    @Test
    void profileValuesStartingWithDash() throws Exception {
        CommandLine cl = parse("-P-a -P -b --activate-profiles -c");
        assertArrayEquals(new String[] {"-a", "-b", "-c"}, cl.getOptionValues(CLIManager.ACTIVATE_PROFILES));
    }

    @Test
    void optionIsNotTakenAsValue() {
        assertThrows(ParseException.class, () -> parse("-D -X"));
    }

    @Test
    void longOptionWithEquals() throws Exception {
        CommandLine cl = parse("--define=foo=bar");
        assertArrayEquals(new String[] {"foo=bar"}, cl.getOptionValues(CLIManager.SET_SYSTEM_PROPERTY));
    }

    @Test
    void unknownOptionIsRejected() {
        assertThrows(ParseException.class, () -> parse("-U"));
    }
}
