/*
 * Copyright © 2022-2026 SNOMED International
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

import {Routes} from '@angular/router';
import {MappingListComponent} from './mapping/mapping-list/mapping-list.component';

// Shared test route table used across several spec files. Kept out of any *.spec.ts file
// because a top-level `export const` in a spec file can make Vitest's test-suite detection
// intermittently fail with "No test suite found in file" (angular/angular-cli#32252).
export const testRoutes: Routes = [
  {
    path: '',
    component: MappingListComponent,
    pathMatch: 'full',
    data: {
      breadcrumb: 'HOME',
      permissions: 'ALL'
    },
  },
  {path: '**', redirectTo: '/'}
];
