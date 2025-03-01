/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.poja.sample;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.Micronaut;

/**
 * This program demonstrates how to use Micronaut HTTP Router without Netty.
 * It reads HTTP requests from stdin and writes HTTP responses to stdout.
 *
 * @author Sahoo.
 */
public class Application {

    public static void main(String[] args) {
        ApplicationContext context = Micronaut.run(Application.class, args);
        context.stop();
    }

}

