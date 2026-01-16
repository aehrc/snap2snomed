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

import {ComponentFixture, TestBed} from '@angular/core/testing';

import {NotesListComponent} from './notes-list.component';
import {RouterTestingModule} from '@angular/router/testing';
import {HttpClientTestingModule} from '@angular/common/http/testing';
import {MatLegacyButtonModule as MatButtonModule} from '@angular/material/legacy-button';
import {MatIconModule} from '@angular/material/icon';
import {MatLegacyCardModule as MatCardModule} from '@angular/material/legacy-card';
import {MatLegacyChipsModule as MatChipsModule} from '@angular/material/legacy-chips';
import {MatLegacyTooltipModule as MatTooltipModule} from '@angular/material/legacy-tooltip';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateLoader, TranslateModule, TranslateService} from '@ngx-translate/core';
import {HttpLoaderFactory} from '../../app.module';
import {APP_CONFIG} from '../../app.config';
import {provideMockStore} from '@ngrx/store/testing';
import {initialAppState} from '../../store/app.state';
import {selectCurrentMapping} from '../../store/mapping-feature/mapping.selectors';
import {MapService} from '../../_services/map.service';
import {DebugElement} from '@angular/core';
import {Mapping} from '../../_models/mapping';
import {MatLegacyInputModule as MatInputModule} from '@angular/material/legacy-input';
import {By} from '@angular/platform-browser';
import {Note, NoteCategory} from '../../_models/note';
import {User} from '../../_models/user';
import {SourceCode} from '../../_models/source_code';
import {Source} from '../../_models/source';
import {MapRow} from '../../_models/map_row';
import {MatLegacyFormFieldModule as MatFormFieldModule} from '@angular/material/legacy-form-field';
import {FormsModule} from '@angular/forms';
import {MatLegacySnackBarModule as MatSnackBarModule} from "@angular/material/legacy-snack-bar";
import {ErrorNotifier} from "../../errorhandler/errornotifier";
import {MatLegacyDialogModule as MatDialogModule, MatLegacyDialogRef as MatDialogRef} from "@angular/material/legacy-dialog";

describe('NotesListComponent', () => {
  let component: NotesListComponent;
  let fixture: ComponentFixture<NotesListComponent>;
  let el: DebugElement;
  const mockMapService = jasmine.createSpyObj('MapService', ['getNotesByMapRow']);
  const mapping = new Mapping();
  mapping.project.title = 'Test Map';
  mapping.id = '0';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        RouterTestingModule,
        HttpClientTestingModule,
        MatButtonModule,
        MatIconModule,
        MatCardModule,
        MatChipsModule,
        MatDialogModule,
        MatTooltipModule,
        MatInputModule,
        MatFormFieldModule,
        MatSnackBarModule,
        NoopAnimationsModule,
        FormsModule,
        TranslateModule.forRoot({
          loader: {
            provide: TranslateLoader,
            useFactory: HttpLoaderFactory,
            deps: [HttpClientTestingModule]
          }
        })
      ],
      providers: [
        {provide: APP_CONFIG, useValue: {}},
        {provide: MatDialogRef, useValue: {}},
        provideMockStore({
          initialState: initialAppState,
          selectors: [
            {selector: selectCurrentMapping, value: mapping},
          ],
        }), TranslateService, ErrorNotifier,
        {provide: MapService, useValue: mockMapService}],
      declarations: [NotesListComponent]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(NotesListComponent);
    component = fixture.componentInstance;
    const row = {
      id: '1', noMap: false, sourceCode: new SourceCode('code', 'display',
        new Source(), '1', []), status: 'DRAFT'
    } as MapRow;
    component.newNote = new Note(null, '', new User(), '', '', row, NoteCategory.USER);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show notes list', () => {
    el = fixture.debugElement.query(By.css('#notes-list'));
    expect(el).toBeTruthy();
  });

  it('should show send button - disabled', () => {
    el = fixture.debugElement.query(By.css('#notes button'));
    expect(el).toBeTruthy();
    expect(el.nativeElement.disabled).toBeTruthy();
  });

  it('should show send button - enabled', () => {
    expect(component.newNote).toBeTruthy();
    if (component.newNote) {
      component.newNote.noteText = 'tests';
      fixture.detectChanges();
      expect(fixture.debugElement.nativeElement.querySelector('#notes button').disabled).toBeFalsy();
    }
  });
});
