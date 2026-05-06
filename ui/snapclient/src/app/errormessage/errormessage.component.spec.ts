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

import {ComponentFixture, fakeAsync, tick, TestBed} from '@angular/core/testing';
import {TranslateLoader, TranslateModule, TranslateService} from '@ngx-translate/core';
import {HttpLoaderFactory} from '../app.module';
import {} from '@angular/common/http/testing';

import {ErrormessageComponent} from './errormessage.component';
import {DebugElement} from '@angular/core';
import {By} from '@angular/platform-browser';
import {ErrorDetail} from '../_models/error_detail';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule } from '@angular/material/sort';

describe('ErrormessageComponent', () => {
  let component: ErrormessageComponent;
  let fixture: ComponentFixture<ErrormessageComponent>;
  let translateService: TranslateService;
  let el: DebugElement;

  beforeEach(fakeAsync(async () => {
    await TestBed.configureTestingModule({
      imports: [
        NoopAnimationsModule,
        MatSnackBarModule,
        MatTableModule,
        MatSortModule,
        TranslateModule.forRoot({
          loader: {
            provide: TranslateLoader,
            useFactory: HttpLoaderFactory
          }
        })
      ],
      providers: [TranslateService],
      declarations: [ErrormessageComponent]
    })
      .compileComponents();
    translateService = TestBed.inject(TranslateService);
    fixture = TestBed.createComponent(ErrormessageComponent);
    translateService = TestBed.inject(TranslateService);

    component = fixture.componentInstance;
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not contain error.message when null passed', fakeAsync(() => {
    component.error.message = undefined;
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    expect(component.error.message).toBeFalsy();
  }));

  it('should contain error.message when string passed', fakeAsync(() => {
    component.error.message = 'something';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    expect(component.error.message).toBeTruthy();
  }));

  it('should not contain error.messages when null passed', fakeAsync(() => {
    component.error.messages = undefined;
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    expect(component.error.messages).toBeFalsy();
  }));

  it('should contain error.messages when array passed', fakeAsync(() => {
    component.error.messages = ['something', 'something else'];
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    expect(component.error.messages).toBeTruthy();
  }));

  it('should contain close button when error showing', fakeAsync(() => {
    component.error.message = 'something';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    el = fixture.debugElement.query(By.css('.close-alert')).nativeElement;
    expect(el).toBeTruthy();
  }));

  it('should not contain detail when error showing but no error.detail', fakeAsync(() => {
    component.error.message = 'something';
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    el = fixture.debugElement.query(By.css('p#detailMessage'));
    expect(el).toBeFalsy();
  }));

  it('should contain detail when error.detail available', fakeAsync(() => {
    component.error.message = 'something';
    component.error.detail = new ErrorDetail();
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    el = fixture.debugElement.query(By.css('p#detailMessage')).nativeElement;
    expect(el).toBeTruthy();
  }));

  it('should contain detail table when error.detail violations available', fakeAsync(() => {
    component.error.message = 'something';
    component.error.detail = new ErrorDetail();
    component.error.detail.violations = [{
      field: 'field[0].subfield',
      message: 'value must be supplied',
    }];
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    el = fixture.debugElement.query(By.css('table#violations')).nativeElement;
    expect(el).toBeTruthy();
  }));

});
