/*
 * Copyright © 2026 SNOMED International
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.snomed.snap2snomed.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfiguration {

  @Bean
  public Hibernate6Module hibernate6Module() {
    Hibernate6Module module = new Hibernate6Module();
    // USE_TRANSIENT_ANNOTATION is enabled by default and causes Jackson to skip
    // @Transient fields during deserialization. This breaks task creation because
    // fields like sourceRowSpecification are @Transient (not persisted) but are
    // sent in the request body and needed by the task creation logic.
    module.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);
    return module;
  }

}
