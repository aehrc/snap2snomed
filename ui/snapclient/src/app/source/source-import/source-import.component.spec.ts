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

import {SourceImportComponent} from './source-import.component';
import {RouterTestingModule} from '@angular/router/testing';
import {HttpClientTestingModule} from '@angular/common/http/testing';
import {UntypedFormBuilder, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {TranslateLoader, TranslateModule, TranslateService} from '@ngx-translate/core';
import {HttpLoaderFactory} from '../../app.module';
import {MockStore, provideMockStore} from '@ngrx/store/testing';
import {IAppState, initialAppState} from '../../store/app.state';
import {MAT_LEGACY_DIALOG_DATA as MAT_DIALOG_DATA, MatLegacyDialogModule as MatDialogModule, MatLegacyDialogRef as MatDialogRef} from '@angular/material/legacy-dialog';
import {MatLegacyButtonModule as MatButtonModule} from '@angular/material/legacy-button';
import {MatDividerModule} from '@angular/material/divider';
import {MatLegacyCardModule as MatCardModule} from '@angular/material/legacy-card';
import {MatLegacyFormFieldModule as MatFormFieldModule} from '@angular/material/legacy-form-field';
import {MatLegacyInputModule as MatInputModule} from '@angular/material/legacy-input';
import {MatLegacySelectModule as MatSelectModule} from '@angular/material/legacy-select';
import {MatLegacyCheckboxModule as MatCheckboxModule} from '@angular/material/legacy-checkbox';
import {DebugElement} from '@angular/core';
import {BrowserAnimationsModule, NoopAnimationsModule} from '@angular/platform-browser/animations';
import {By} from '@angular/platform-browser';
import {ErrormessageComponent} from '../../errormessage/errormessage.component';
import { APP_CONFIG } from '../../app.config';
import { MatLegacySnackBarModule as MatSnackBarModule } from '@angular/material/legacy-snack-bar';
import { MatLegacyTooltipModule as MatTooltipModule } from '@angular/material/legacy-tooltip';

describe('SourceImportComponent', () => {
  let component: SourceImportComponent;
  let fixture: ComponentFixture<SourceImportComponent>;
  let translateService: TranslateService;
  let store: MockStore<IAppState>;
  let el: DebugElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        RouterTestingModule,
        HttpClientTestingModule,
        BrowserAnimationsModule,
        FormsModule,
        MatDialogModule,
        MatButtonModule,
        MatDividerModule,
        MatCardModule,
        MatFormFieldModule,
        ReactiveFormsModule,
        MatInputModule,
        MatSelectModule,
        NoopAnimationsModule,
        MatSnackBarModule,
        MatCheckboxModule,
        MatTooltipModule,
        TranslateModule.forRoot({
          loader: {
            provide: TranslateLoader,
            useFactory: HttpLoaderFactory,
            deps: [HttpClientTestingModule]
          }
        })
      ],
      providers: [{ provide: APP_CONFIG, useValue: {} },
        provideMockStore({initialState: initialAppState}), TranslateService, UntypedFormBuilder,
        {provide: MatDialogRef, useValue: {}}, {provide: MAT_DIALOG_DATA, useValue: {}}],
      declarations: [SourceImportComponent, ErrormessageComponent]
    })
      .compileComponents();
    translateService = TestBed.inject(TranslateService);
    store = TestBed.inject(MockStore);
    fixture = TestBed.createComponent(SourceImportComponent);
    component = fixture.componentInstance;
    component.translate = translateService;
    component.ngOnInit();
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have close button', () => {
    fixture.detectChanges();
    el = fixture.debugElement.query(By.css('button[type="cancel"]')).nativeElement;
    expect(el).toBeTruthy();
  });

  it('should have disabled save button if file uploaded but no name or version', () => {
    const blob = new Blob([''], {type: 'text/csv'});
    component.data.source_file = blob as File;
    fixture.detectChanges();
    el = fixture.debugElement.query(By.css('button[type="submit"]'));
    expect(el).toBeTruthy();
    expect(el.nativeElement.disabled).toBeTruthy();
  });

  it('should have save button if file uploaded and name and version supplied', () => {
    component.data.name = 'source name';
    component.data.version = 'source version';
    component.data.additionalColumnIndexes = [];
    component.data.additionalColumnTypes = [];
    const blob = new Blob([''], {type: 'text/csv'});
    component.data.source_file = blob as File;
    fixture.detectChanges();
    el = fixture.debugElement.query(By.css('button[type="submit"]')).nativeElement;
    expect(el).toBeTruthy();
  });

  it('should have a file upload input', () => {
    fixture.detectChanges();
    el = fixture.debugElement.query(By.css('input[type="file"]')).nativeElement;
    expect(el).toBeTruthy();
  });

  it('should have a delimiter input', () => {
    fixture.detectChanges();
    el = fixture.debugElement.query(By.css('input[id="delimiter"]')).nativeElement;
    expect(el).toBeTruthy();
  });

  it('should show columns if file loaded correctly', () => {
    component.lines = ['1', '2'];
    component.data.additionalColumnIndexes = [];
    fixture.detectChanges();
    el = fixture.debugElement.query(By.css('#lines'));
    expect(el).toBeTruthy();
    expect(el.nativeElement.textContent).toContain('SOURCE.SELECT_COLUMNS');
  });


});
