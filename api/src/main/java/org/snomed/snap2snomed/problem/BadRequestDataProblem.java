/*
 * Copyright © 2022 SNOMED International
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

package org.snomed.snap2snomed.problem;

import org.zalando.problem.Status;

public class BadRequestDataProblem extends Snap2SnomedProblem {

  private static final long serialVersionUID = 1L;

  private static final String BASE_TYPE = "bad-request-data/";

  public BadRequestDataProblem(String detail) {
    super(BASE_TYPE, "Bad request data", Status.BAD_REQUEST, detail);
  }
}
