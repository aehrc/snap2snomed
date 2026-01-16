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

import {ErrorHandler, NgModule, NgZone} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {AppRoutingModule} from './app-routing.module';
import {AppComponent} from './app.component';
import {MatLegacyDialogModule as MatDialogModule} from '@angular/material/legacy-dialog';
import {MatLegacyButtonModule as MatButtonModule} from '@angular/material/legacy-button';
import {MatDividerModule} from '@angular/material/divider';
import {AuthService} from './_services/auth.service';
import {AuthGuard} from './auth.guard';
import {HTTP_INTERCEPTORS, HttpClient, HttpClientModule} from '@angular/common/http';
import {StoreModule} from '@ngrx/store';
import {StoreDevtoolsModule} from '@ngrx/store-devtools';
import {EffectsModule} from '@ngrx/effects';
import {HomeComponent} from './home/home.component';
import {appReducers} from './store/app.reducers';
import {getInitialState} from './store/app.state';
import {AuthEffects} from './store/auth-feature/auth.effects';
import {UserService} from './_services/user.service';
import {MatIconModule} from '@angular/material/icon';
import {MatLegacyListModule as MatListModule} from '@angular/material/legacy-list';
import {MatLegacyMenuModule as MatMenuModule} from '@angular/material/legacy-menu';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateLoader, TranslateModule, TranslateService} from '@ngx-translate/core';
import {TranslateHttpLoader} from '@ngx-translate/http-loader';
import {MatToolbarModule} from '@angular/material/toolbar';
import {MappingAddComponent} from './mapping/mapping-add/mapping-add.component';
import {MappingViewComponent} from './mapping/mapping-view/mapping-view.component';
import {MappingListComponent} from './mapping/mapping-list/mapping-list.component';
import {MatLegacyCardModule as MatCardModule} from '@angular/material/legacy-card';
import {MAT_LEGACY_FORM_FIELD_DEFAULT_OPTIONS as MAT_FORM_FIELD_DEFAULT_OPTIONS, MatLegacyFormFieldModule as MatFormFieldModule} from '@angular/material/legacy-form-field';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MapService} from './_services/map.service';
import {MAT_DATE_LOCALE} from '@angular/material/core';
import {MatLegacyInputModule as MatInputModule} from '@angular/material/legacy-input';
import {BreadcrumbModule} from 'xng-breadcrumb';
import {MappingEffects} from './store/mapping-feature/mapping.effects';
import {MatGridListModule} from '@angular/material/grid-list';
import {MatLegacyChipsModule as MatChipsModule} from '@angular/material/legacy-chips';
import {InitialsPipe} from './_utils/initialize_pipe';
import {LastupdatedPipe} from './_utils/lastupdated_pipe';
import {ErrormessageComponent} from './errormessage/errormessage.component';
import {SourceImportComponent} from './source/source-import/source-import.component';
import {MatLegacySelectModule as MatSelectModule} from '@angular/material/legacy-select';
import {MatLegacyCheckboxModule as MatCheckboxModule} from '@angular/material/legacy-checkbox';
import {SourceEffects} from './store/source-feature/source.effects';
import {MatLegacyProgressSpinnerModule as MatProgressSpinnerModule} from '@angular/material/legacy-progress-spinner';
import {MatLegacyProgressBarModule as MatProgressBarModule} from '@angular/material/legacy-progress-bar';
import {MatSortModule} from '@angular/material/sort';
import {MatLegacyPaginatorModule as MatPaginatorModule} from '@angular/material/legacy-paginator';
import {MatLegacyTableModule as MatTableModule} from '@angular/material/legacy-table';
import {MatLegacyTabsModule as MatTabsModule} from '@angular/material/legacy-tabs';
import {LoadingSpinnerComponent} from './loading-spinner/loading-spinner.component';
import {MatTableFilterModule} from 'mat-table-filter';
import {FhirEffects} from './store/fhir-feature/fhir.effects';
import {metaReducers} from './store/store.module';
import {Router} from '@angular/router';
import * as Sentry from '@sentry/angular';
import {APP_CONFIG} from './app.config';
import {TokenInterceptor} from './_services/token-interceptor.service';
import {ConceptSearchComponent} from './concept-search/concept-search.component';
import {AssignedWorkComponent} from './task/assigned-work/assigned-work.component';
import {TaskEffects} from './store/task-feature/task.effects';
import {MappingWorkComponent} from './mapping/mapping-work/mapping-work.component';
import {MappingTableComponent} from './mapping/mapping-table/mapping-table.component';
import {MatExpansionModule} from '@angular/material/expansion';
import {DraggableDirective} from './_directives/draggable.directive';
import {DroppableDirective} from './_directives/droppable.directive';
import {ConceptPropertiesComponent} from './concept-properties/concept-properties.component';
import {TaskCardComponent} from './task/task-card/task-card.component';
import {MatLegacySnackBarModule as MatSnackBarModule} from '@angular/material/legacy-snack-bar';
import {MatLegacyRadioModule as MatRadioModule} from '@angular/material/legacy-radio';
import {MatBadgeModule} from '@angular/material/badge';
import {TargetVersionComponent} from './target-version/target-version.component';
import {TaskAddComponent} from './task/task-add/task-add.component';
import {ErrorNotifier} from './errorhandler/errornotifier';
import {Snap2SnomedErrorHandler} from './errorhandler/snap2snomederrorhandler';
import {Snap2SnomedHttpErrorInterceptor} from './errorhandler/snap2snomedhttperrorinterceptor';
import {TrimPipe} from './_utils/trim_pipe';
import {TaskItemComponent} from './task/task-item/task-item.component';
import {MatLegacyTooltipModule as MatTooltipModule} from '@angular/material/legacy-tooltip';
import {UserChipComponent} from './user/user-chip/user-chip.component';
import {MatSidenavModule} from '@angular/material/sidenav';
import {TaskSelectComponent} from './task/task-select/task-select.component';
import {MappingDetailComponent} from './mapping/mapping-detail/mapping-detail.component';
import {MappingDetailsCardComponent} from './mapping/mapping-details-card/mapping-details-card.component';
import {SourceDetailComponent} from './source/source-detail/source-detail.component';
import {TargetRelationshipComponent} from './mapping/target-relationship/target-relationship.component';
import {TargetRowsComponent} from './mapping/target-rows/target-rows.component';
import {DragDropModule} from '@angular/cdk/drag-drop';
import {ConfirmDialogComponent} from './dialog/confirm-dialog/confirm-dialog.component';
import {GravatarComponent} from './user/gravatar/gravatar.component';
import {ClipboardModule} from '@angular/cdk/clipboard';
import {MatLegacySlideToggleModule as MatSlideToggleModule} from '@angular/material/legacy-slide-toggle';
import {NotesItemComponent} from './notes/notes-item/notes-item.component';
import {NotesListComponent} from './notes/notes-list/notes-list.component';
import {ConceptAutosuggestComponent} from './concept-search/concept-autosuggest/concept-autosuggest.component';
import {ConceptListComponent} from './concept-search/concept-list/concept-list.component';
import {ProjectRolesComponent} from './project-roles/project-roles.component';
import {ProjectBadgesComponent} from './project-badges/project-badges.component';
import {TaskCreateComponent} from './task/task-create/task-create.component';
import {ShrimpHierarchyViewModule} from '@csiro/shrimp-hierarchy-view';
import {TreeViewComponent} from './tree-view/tree-view.component';
import {BulkchangeComponent} from './mapping/bulkchange/bulkchange.component';
import {MatTreeModule} from '@angular/material/tree';
import {AutomapDialogComponent} from './automap/automap-dialog.component';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {ResultsdialogComponent} from './resultsdialog/resultsdialog.component';
import {MatBottomSheetModule} from '@angular/material/bottom-sheet';
import {AutomapComponent} from './automap/automap.component';
import {MappingTableSelectorComponent} from './mapping/mapping-table-selector/mapping-table-selector.component';
import {NotauthorizedComponent} from './notauthorized/notauthorized.component';
import {LogoutComponent} from './logout/logout.component';
import { MappingImportComponent } from './mapping/mapping-import/mapping-import.component';
import { FooterComponent } from './footer/footer.component';
import { FeedbackWidgetComponent } from './feedback-widget/feedback-widget.component';
import { AcceptTermsComponent } from './accept-terms/accept-terms.component';
import { MappingNotesComponent } from './mapping/mapping-table-notes/mapping-notes.component';
import { ResizeColumnComponent } from './column-resize/resize-column.component';

// AoT requires an exported function for factories
export function HttpLoaderFactory(httpClient: HttpClient): TranslateHttpLoader {
  return new TranslateHttpLoader(httpClient);
}


@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    MappingAddComponent,
    MappingViewComponent,
    MappingListComponent,
    InitialsPipe,
    LastupdatedPipe,
    TrimPipe,
    ErrormessageComponent,
    SourceImportComponent,
    LoadingSpinnerComponent,
    ConceptSearchComponent,
    AssignedWorkComponent,
    MappingWorkComponent,
    AutomapDialogComponent,
    MappingTableComponent,
    MappingNotesComponent,
    DraggableDirective,
    DroppableDirective,
    ConceptPropertiesComponent,
    TaskCardComponent,
    TargetVersionComponent,
    TaskAddComponent,
    TaskItemComponent,
    UserChipComponent,
    TaskSelectComponent,
    MappingDetailComponent,
    MappingDetailsCardComponent,
    SourceDetailComponent,
    TargetRelationshipComponent,
    TargetRowsComponent,
    ConfirmDialogComponent,
    GravatarComponent,
    ConceptAutosuggestComponent,
    ConceptListComponent,
    NotesItemComponent,
    NotesListComponent,
    ProjectRolesComponent,
    ProjectBadgesComponent,
    TaskCreateComponent,
    BulkchangeComponent,
    TreeViewComponent,
    AutomapComponent,
    ResultsdialogComponent,
    MappingTableSelectorComponent,
    NotauthorizedComponent,
    LogoutComponent,
    MappingImportComponent,
    FooterComponent,
    FeedbackWidgetComponent,
    AcceptTermsComponent,
    ResizeColumnComponent
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    StoreModule.forRoot(appReducers, {initialState: getInitialState(), metaReducers}),
    StoreDevtoolsModule.instrument({maxAge: 10}),
    EffectsModule.forRoot([AuthEffects, MappingEffects, SourceEffects, FhirEffects, TaskEffects]),
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient]
      },
    }),
    HttpClientModule,
    BreadcrumbModule,
    MatDialogModule,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatToolbarModule,
    MatCardModule,
    MatFormFieldModule,
    FormsModule,
    ReactiveFormsModule,
    MatInputModule,
    MatGridListModule,
    MatChipsModule,
    MatSelectModule,
    MatCheckboxModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatTableFilterModule,
    MatTabsModule,
    MatExpansionModule,
    MatSnackBarModule,
    MatRadioModule,
    MatBadgeModule,
    MatTooltipModule,
    MatTreeModule,
    MatSidenavModule,
    DragDropModule,
    ClipboardModule,
    MatBottomSheetModule,
    MatSlideToggleModule,
    ShrimpHierarchyViewModule,
    MatButtonToggleModule,
  ],
  exports: [MatToolbarModule, MatButtonModule, TranslateModule],
  providers: [AuthService, AuthGuard, UserService, MapService, TranslateService,
    {provide: MAT_DATE_LOCALE, useValue: 'en-AU'},
    {provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: {appearance: 'fill'}},
    {
      provide: ErrorHandler,
      useClass: Snap2SnomedErrorHandler,
      deps: [APP_CONFIG, ErrorNotifier, TranslateService, NgZone]
    },
    {provide: Sentry.TraceService, deps: [Router]},
    {provide: HTTP_INTERCEPTORS, useClass: TokenInterceptor, multi: true},
    {
      provide: HTTP_INTERCEPTORS,
      useClass: Snap2SnomedHttpErrorInterceptor,
      multi: true,
      deps: [Snap2SnomedErrorHandler]
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {
  constructor(trace: Sentry.TraceService) {
  }
}
