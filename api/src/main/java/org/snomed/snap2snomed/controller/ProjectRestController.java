package org.snomed.snap2snomed.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.snomed.snap2snomed.controller.dto.ImportMappingFileDetails;
import org.snomed.snap2snomed.model.ImportedCodeSet;
import org.snomed.snap2snomed.model.Map;
import org.snomed.snap2snomed.model.Project;
import org.snomed.snap2snomed.model.User;
import org.snomed.snap2snomed.problem.MappingImportProblem;
import org.snomed.snap2snomed.problem.auth.NotAuthorisedProblem;
import org.snomed.snap2snomed.repository.ImportedCodeSetRepository;
import org.snomed.snap2snomed.repository.MapRepository;
import org.snomed.snap2snomed.repository.ProjectRepository;
import org.snomed.snap2snomed.repository.UserRepository;
import org.snomed.snap2snomed.repository.handler.MapEventHandler;
import org.snomed.snap2snomed.repository.handler.ProjectEventHandler;
import org.snomed.snap2snomed.security.AuthenticationFacade;
import org.snomed.snap2snomed.security.WebSecurity;
import org.snomed.snap2snomed.service.CodeSetImportService;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

@Validated
@RestController
public class ProjectRestController {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ProjectRepository projectRepository;

  @Autowired
  private ProjectEventHandler projectEventHandler;

  @Autowired
  private MapRepository mapRepository;

  @Autowired
  private MapEventHandler mapEventHandler;

  @Autowired
  private ImportedCodeSetRepository importedCodeSetRepository;

  @Autowired
  private CodeSetImportService importService;

  @Autowired
  AuthenticationFacade authenticationFacade;  

  @Autowired
  private WebSecurity webSecurity;

  @Transactional
  @Operation
  @PostMapping(value = "/project/create", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}, produces = {MediaType.APPLICATION_JSON_VALUE})
  Map createProject(
    @Parameter(description = "Project details for project creation", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
      @RequestPart(name = "project",
      required = true) @Validated Project project,
    @Parameter(description = "Map details for map creation", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
      @RequestPart(name = "map",
      required = true) @Validated Map map,
    @Parameter(description = "Details of the file being imported", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
      @RequestPart(name = "mappingFileDetails",
      required = false) @Validated ImportMappingFileDetails mappingFileDetails,
    @Parameter(description = "The file to be imported",content = {@Content(mediaType = "text/tsv"), @Content(mediaType = "text/csv")})
      @RequestPart(name = "file",
      required = false) MultipartFile file)
      throws HttpMediaTypeNotAcceptableException {

    // Any valid user can create a new map
    if (!webSecurity.isValidUser()) {
      throw new NotAuthorisedProblem("Only valid users can create a project and associated map");
    }

    // Create the project and handle all before create events
    project.setOwners(refreshUsers(project.getOwners()));
    project.setMembers(refreshUsers(project.getMembers()));
    project.setGuests(refreshUsers(project.getGuests()));
    projectEventHandler.handleProjectBeforeCreate(project);
    // Our handleProjectBeforeSave is an update check that requires
    // a valid existing projectId so we cannot run it here
    // We only need the following checks anyway
    ProjectEventHandler.checkAtLeastOneOwner(project.getOwners());
    ProjectEventHandler.checkForUsersInMultipleRoles(
        project.getOwners(), project.getMembers(), project.getGuests());
    Project theProject = projectRepository.save(project);

    // Create the associated Map and handle all before and after create events
    map.getProject().setId(theProject.getId());
    ImportedCodeSet source = importedCodeSetRepository.findById(map.getSource().getId()).orElseThrow(() ->
      Problem.valueOf(Status.BAD_REQUEST,  "Specified Imported Code Set doesn't exist [id= " + map.getSource().getId() + "]")
    );
    map.setSource(source);
    mapEventHandler.handleMapBeforeCreate(map);
    mapEventHandler.handleBeforeSave(map);
    Map theMap = mapRepository.save(map);
    // Create the initial MapRows for the map
    mapEventHandler.handleMapAfterCreate(theMap);

    // Import mapping file if it's specified
    if (mappingFileDetails != null) {
      if (file == null) {
        throw new MappingImportProblem("no-file-provided", "No file provided for mapping file import");
      }
      mappingFileDetails.setMapId(theMap.getId().toString());
      importService.importMappings(mappingFileDetails, file);
    }
    return theMap;

  }

  private Set<User> refreshUsers(Set<@NotNull User> users) {
    Set<User> actualUsers = new HashSet<User>();
    users.forEach(user -> {
      User existingUser = userRepository.findById(user.getId()).orElseThrow(() -> 
        Problem.valueOf(Status.BAD_REQUEST,  "User doesn't exist [id= " + user.getId() + "]"));
        actualUsers.add(existingUser);
    });
    return actualUsers;
  }


}
