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

export class FormUtils {

  static readonly MAX_TITLE = 100;
  static readonly MAX_VERSION = 30;
  static readonly MAX_DESCRIPTION = 200;
  static readonly MAX_TARGETSCOPE = 1024;
  static readonly MAX_NOTE = 255;
  static readonly MAX_TASK_DESCRIPTION = 60;
  static readonly VALID_STRING_PATTERN = /^\S+.*/;

  /**
   * Used to calculate the next version of a mapping and a source
   * @param currentVersion the current version of the mapping or the source
   */
  static calculateNextVersion(currentVersion: string) : string {

    // Check if the version is a valid integer or dot-separated number
    const isIntegerOrDotNumber = /^\d+(\.\d+)*$/.test(currentVersion);
    
    // If not a valid integer or dot-separated number, append ".1" and return
    if (!isIntegerOrDotNumber) {
        return currentVersion + ".1";
    }

    const parts = currentVersion.split('.');
    const versionNumbers = parts.map(part => parseInt(part, 10));
    versionNumbers[versionNumbers.length - 1]++;
    const newVersion = versionNumbers.join('.');

    return newVersion;
  }
};
