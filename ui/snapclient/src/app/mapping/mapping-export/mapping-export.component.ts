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

import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {TranslateService} from '@ngx-translate/core';
import {Store} from '@ngrx/store';
import {IAppState} from '../../store/app.state';
import {APP_CONFIG, AppConfig} from '../../app.config';
import {ErrorInfo} from '../../errormessage/errormessage.component';
import { FormBuilder } from '@angular/forms';

@Component({
  selector: 'app-mapping-export',
  templateUrl: './mapping-export.component.html',
  styleUrls: ['./mapping-export.component.css'],
})
export class MappingExportComponent implements OnInit, OnDestroy {

  error: ErrorInfo = {};

  fileType = this._formBuilder.control(null);
  columns = this._formBuilder.group({
    all: false,
    notes: false,
    author: false,
    reviewer: false,
  });


  // exportForm = new FormGroup({
  //   fileType: new FormControl(''),
  //   columns: new FormGroup({
  //     all: new FormControl(true),
  //     notes: new FormControl(''),
  //     author: new FormControl(''),
  //     reviewer: new FormControl('')
  //   })
  // });
  
  constructor(@Inject(APP_CONFIG) private config: AppConfig,
              public dialogRef: MatDialogRef<MappingExportComponent>,
              public translate: TranslateService,
              private store: Store<IAppState>,
              private _formBuilder: FormBuilder,
              @Inject(MAT_DIALOG_DATA) public data: {
                //source: MappingExportSource,
                createMode: boolean
              }) {
    dialogRef.disableClose = true;


  }

  

  fileTypeChanged(): void {

  }

  ngOnDestroy(): void {
    this.error = {};
  }

  ngOnInit(): void {


  }

  onSubmit(): void {
    let returnVal = {
      'fileType' :  this.fileType.value,
      'includeLatestNote' : this.columns.get('notes')?.value,
      'includeLatestAuthor' : this.columns.get('author')?.value,
      'includeLatestReviewer' : this.columns.get('reviewer')?.value,
    };
    this.dialogRef.close(returnVal);
  }

  onClose(): void {
    this.dialogRef.close(false);
  }

  disableSubmit(): boolean {
    return false;
  }

}
