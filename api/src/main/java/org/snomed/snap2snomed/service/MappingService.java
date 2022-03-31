package org.snomed.snap2snomed.service;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.snomed.snap2snomed.controller.dto.*;
import org.snomed.snap2snomed.model.Map;
import org.snomed.snap2snomed.model.*;
import org.snomed.snap2snomed.model.enumeration.MapStatus;
import org.snomed.snap2snomed.model.enumeration.TaskType;
import org.snomed.snap2snomed.problem.Snap2SnomedProblem;
import org.snomed.snap2snomed.problem.auth.NotAuthorisedProblem;
import org.snomed.snap2snomed.problem.mapping.InvalidBulkChangeProblem;
import org.snomed.snap2snomed.problem.mapping.InvalidMappingProblem;
import org.snomed.snap2snomed.problem.mapping.UnauthorisedMappingProblem;
import org.snomed.snap2snomed.repository.*;
import org.snomed.snap2snomed.repository.dto.MapRowTargetsForMapRowDto;
import org.snomed.snap2snomed.repository.handler.MapRowEventHandler;
import org.snomed.snap2snomed.repository.handler.MapRowTargetEventHandler;
import org.snomed.snap2snomed.security.AuthenticationFacade;
import org.snomed.snap2snomed.security.WebSecurity;
import org.snomed.snap2snomed.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MappingService {

  @PersistenceContext
  private EntityManager em;

  @Autowired
  MapRepository mapRepository;

  @Autowired
  MapRowRepository mapRowRepository;

  @Autowired
  MapRowTargetRepository mapRowTargetRepository;

  @Autowired
  TaskRepository taskRepository;

  @Autowired
  MapRowEventHandler mapRowEventHandler;

  @Autowired
  MapViewService mapViewService;

  @Autowired
  FhirService fhirService;

  @Autowired
  MapRowTargetEventHandler mapRowTargetEventHandler;

  @Autowired
  AuthenticationFacade authenticationFacade;

  @Autowired
  ImportedCodeSetRepository importedCodeSetRepository;

  @Autowired
  WebSecurity webSecurity;

  private static enum RowChange {
    STATUS_CHANGED, ROW_CHANGED, NO_CHANGE
  }

  @Data
  @NoArgsConstructor
  private class RowChanges {
    private RowChange rowChange;
    private Set<MapRow> mapRowsToSave = new HashSet<MapRow>();
    private Set<MapRowTarget> mapRowTargetsToSave = new HashSet<MapRowTarget>();
    private Set<MapRowTarget> mapRowTargetsToDelete = new HashSet<MapRowTarget>();
  }

  @Transactional
  public MappingResponse updateMapping(MappingUpdateDto mappings) {
    if (mappings.getMappingDetails() == null || mappings.getMappingDetails().isEmpty()) {
      throw new InvalidBulkChangeProblem("No changes were specified");
    }
    AtomicLong rowCount = new AtomicLong(0L);
    AtomicLong updatedRowCount = new AtomicLong(0L);
    User currentUser = authenticationFacade.getAuthenticatedUser();
    RowChanges rowChanges = new RowChanges();

    final Task task = getTask(mappings);
    if (task != null && !EntityUtils.isTaskAssignee(currentUser, task)) {
      throw new UnauthorisedMappingProblem("User is not assigned to task " + task.getId());
    }

    // already asserted the list is not empty
    MappingDetails firstMappingDetails = mappings.getMappingDetails().get(0);

    Map map = mapRowRepository.findById(firstMappingDetails.getRowId())
        .orElseThrow(() -> new InvalidMappingProblem("MapRow", firstMappingDetails.getRowId())).getMap();

    if (task == null && !(map.getProject().getOwners().contains(currentUser) || webSecurity.isAdminUser())) {
      throw new UnauthorisedMappingProblem(
          "Only an owner can perform bulk updates outside of the context of a task");
    }

    mappings.getMappingDetails().forEach(mappingDetails -> {
      validateMappingUpdates(mappingDetails.getMappingUpdate());

      MapRow mapRow = mapRowRepository
          .findById(mappingDetails.getRowId())
          .orElseThrow(() -> new InvalidMappingProblem("MapRow", mappingDetails.getRowId()));

      if (!map.getId().equals(mapRow.getMap().getId())) {
        throw new InvalidBulkChangeProblem(
            "Specified map rows for a bulk change must all be from the same map, first change referenced map " + map.getId()
                + " this row references map " + mapRow.getMap().getId());
      }

      if (task != null) {
        if (!rowIsAssignedToTask(task, mapRow)) {
          throw new InvalidBulkChangeProblem("Specified task " + task.getId() + " is not associated with map row " + mapRow.getId());
        }
      }

      Set<MapRowTarget> mapRowTargets = new HashSet<>();
      if (mappingDetails.getMappingUpdate().getTargetId() != null && mappingDetails.getMappingUpdate().getNoMap() == null) {
        MapRowTarget mapRowTarget = mapRowTargetRepository
            .findById(mappingDetails.getMappingUpdate().getTargetId())
            .orElseThrow(() -> new InvalidMappingProblem("MapRowTarget", mappingDetails.getMappingUpdate().getTargetId()));
        if (!mapRowTarget.getRow().getId().equals(mapRow.getId())) {
          throw new InvalidBulkChangeProblem("Map row target " + mapRowTarget.getId() + " does not belong to map row " + mapRow.getId());
        }
        mapRowTargets = Set.of(mapRowTarget);
      }
      // Clear Status if NO MAP is set or unset as it will be set automatically
      if (mappingDetails.getMappingUpdate().getNoMap() != null) {
        mappingDetails.getMappingUpdate().setStatus(null);
      }

      rowCount.incrementAndGet(); // Increment row count for Row
      RowChanges mapRowChanges = applyMapRowChanges(mapRow, mappingDetails.getMappingUpdate(), mapRowTargets.size(), task, currentUser);
      if ( mapRowChanges.getRowChange() != RowChange.NO_CHANGE && (mapRowTargets.size() == 0
          || (mapRowTargets.size() == 0 && mappingDetails.getMappingUpdate().getStatus() != null))) {
        rowChanges.getMapRowsToSave().addAll(mapRowChanges.getMapRowsToSave());
        updatedRowCount.incrementAndGet(); // Only add updated count if there is not MapRowTargets
      }
      if (mapRowTargets.size() > 0) {
        RowChanges mapRowTargetChanges = applyMapRowTargetChanges(mapRowTargets.iterator().next(), mappingDetails.getMappingUpdate(), task,
          mapRowChanges.getRowChange(), currentUser);
        if (mapRowTargetChanges.getRowChange() == RowChange.ROW_CHANGED) {
          rowChanges.getMapRowTargetsToSave().addAll(mapRowTargetChanges.getMapRowTargetsToSave());
          rowChanges.getMapRowTargetsToDelete().addAll(mapRowTargetChanges.getMapRowTargetsToDelete());
          updatedRowCount.incrementAndGet();
        }
      }
    });
    // Save everything in one go not resource by resource
    mapRowRepository.saveAll(rowChanges.getMapRowsToSave());
    mapRowTargetRepository.saveAll(rowChanges.getMapRowTargetsToSave());
    mapRowTargetRepository.deleteAll(rowChanges.getMapRowTargetsToDelete());
    em.flush();
    em.clear();

    return MappingResponse.builder()
        .rowCount(rowCount.get())
        .updatedRowCount(updatedRowCount.get())
        .build();
  }

  @Transactional
  public MappingResponse updateMappingForSelection(MappingUpdateDto mappings) {
    log.info("Start Bulk edit for selection");
    MappingUpdateDto mapUpdate = new MappingUpdateDto();
    List<MappingDetails> mappingDetails = new ArrayList<MappingDetails>();
    mappings.getMappingDetails().forEach(mappingDetail -> {
      mappingDetail.getSelection().forEach(selection -> {
        mappingDetails.add(
          MappingDetails.builder()
            .rowId(selection.getMapRowId())
            .taskId(mappingDetail.getTaskId())
            .mappingUpdate(MappingDto.builder()
              .noMap(mappingDetail.getMappingUpdate().getNoMap())
              .relationship(mappingDetail.getMappingUpdate().getRelationship())
              .status(mappingDetail.getMappingUpdate().getStatus())
              .clearTarget(mappingDetail.getMappingUpdate().getClearTarget())
              .targetId(selection.getMapRowTargetId())
              .build()
              )
            .build());
      });
    });
    mapUpdate.setMappingDetails(mappingDetails);
    return this.updateMapping(mapUpdate);
  }

  @Transactional
  public MappingResponse updateMappingForTask(Long taskId, MappingDto mappingUpdate) {
    log.info("Start Bulk edit for task " + taskId);
    validateMappingUpdates(mappingUpdate);
    Task task = taskRepository
        .findById(taskId)
        .orElseThrow(() -> new InvalidMappingProblem("Task", taskId));
    if (!webSecurity.isAdminUser() && !webSecurity.hasAnyProjectRoleForMapId(task.getMap().getId())) {
      throw new NotAuthorisedProblem("Not authorised to view map if the user is not admin or member of an associated project!");
    }

    return updateMapRowsForMappingDto(mappingUpdate, task, null);

  }

  @Transactional
  public MappingResponse updateMappingForMap(Long mapId, MappingDto mappingUpdates) {
    log.info("Start Bulk edit for map " + mapId);
    validateMappingUpdates(mappingUpdates);
    MappingResponse retVal = updateMapRowsForMappingDto(mappingUpdates, null, mapId);
    return retVal;
  }

  private void validateMappingUpdates(MappingDto mappingUpdates) {
    if (mappingUpdates.isNoChange()) {
      throw new InvalidBulkChangeProblem("No changes were specified");
    } else if (!mappingUpdates.isValid()) {
      throw new InvalidBulkChangeProblem(
          "Invalid combination of changes. Clear/set 'no map' and clearing targets must be done independently of any other changes");
    } else if (mappingUpdates.getStatus() != null && mappingUpdates.getStatus().equals(MapStatus.UNMAPPED)) {
      throw new InvalidBulkChangeProblem(
          "Cannot set status to UNMAPPED, UNMAPPED is a system controlled state when a row has no targets and no map is not set");
    }
  }

  private MappingResponse updateMapRowsForMappingDto(MappingDto mappingUpdate, Task task, Long id) {
    Long mapId = 0l;
    List<MapRow> mapRows = new ArrayList<MapRow>();
    if (task == null) {
      mapRows = mapRowRepository.findMapRowsByMapId(id);
      mapId = id;

    } else {
      mapRows = mapRowRepository.findMapRowsByTaskId(task.getId());
      mapId = task.getMap().getId();
    }
    // Clear Status if NO MAP is set or unset as it will be set automatically
    if (mappingUpdate.getNoMap() != null) {
      mappingUpdate.setStatus(null);
    }
    // Only call getAuthenticatedUser once
    User currentUser = authenticationFacade.getAuthenticatedUser();
    AtomicLong rowCount = new AtomicLong(0L);
    AtomicLong updatedRowCount = new AtomicLong(0L);
    RowChanges rowChanges = new RowChanges();

    // Create a map of MapRow and associated MapRowTargets that we can iterate through.
    // We can get the MapRowTargets with a fast JPQL query and creating the java map from
    // the results is fast too.
    // This way we only query the database once instead of querying it for every MapRow
    List<MapRowTargetsForMapRowDto> allMapRowTargets =
        new ArrayList<MapRowTargetsForMapRowDto>(mapRowTargetRepository.findTargetsByMapId(mapId));
    HashMap<MapRow,  List<MapRowTarget>> mapRowTargetsMap = new HashMap<MapRow,  List<MapRowTarget>>();
    allMapRowTargets.forEach(mapRowTargetsForMapRowDto ->{
      if (mapRowTargetsMap.get(mapRowTargetsForMapRowDto.getMapRow()) != null) {
        mapRowTargetsMap.get(mapRowTargetsForMapRowDto.getMapRow()).add(mapRowTargetsForMapRowDto.getMapRowTarget());
      } else {
        List<MapRowTarget> mpRowTarget = new ArrayList<MapRowTarget>();
        if (mapRowTargetsForMapRowDto.getMapRowTarget() != null) {
          mpRowTarget = new ArrayList<>(Arrays.asList(mapRowTargetsForMapRowDto.getMapRowTarget()));
        }
        mapRowTargetsMap.put(mapRowTargetsForMapRowDto.getMapRow(), mpRowTarget);
      }
    });
    mapRows.forEach(row -> {
      rowCount.incrementAndGet();
      List<MapRowTarget> mapRowTargets = new ArrayList<MapRowTarget>();
      if (mapRowTargetsMap.get(row) != null) {
        mapRowTargets = mapRowTargetsMap.get(row);
      }
      int mapRowTargetCount = mapRowTargets.size();
      RowChanges mapRowChanges = applyMapRowChanges(row, mappingUpdate, mapRowTargetCount, task, currentUser);
      RowChange mapRowChanged = mapRowChanges.getRowChange();
      if (mapRowChanged != RowChange.NO_CHANGE && (mapRowTargetCount == 0 || mappingUpdate.getStatus() != null)) {
        // We will calculate maprows in the update result
        rowChanges.getMapRowsToSave().addAll(mapRowChanges.getMapRowsToSave());
        updatedRowCount.incrementAndGet();
      }
      if (mapRowTargetCount > 0) {
        rowCount.decrementAndGet(); // We will be adding the MapRowTarget count as row counts
        if (mapRowChanged == RowChange.STATUS_CHANGED) {
          updatedRowCount.decrementAndGet(); // Will calculate updatedRowCount with the mapRowTargets as there could be 1 to many mappings
        }
      }
      mapRowTargets.forEach(mapRowTarget -> {
        rowCount.incrementAndGet();
        RowChanges mapRowTargetChanges = applyMapRowTargetChanges(mapRowTarget, mappingUpdate, task, mapRowChanged, currentUser);
        if (mapRowTargetChanges.getRowChange() == RowChange.ROW_CHANGED) {
          updatedRowCount.incrementAndGet();
          rowChanges.getMapRowTargetsToSave().addAll(mapRowTargetChanges.getMapRowTargetsToSave());
          rowChanges.getMapRowTargetsToDelete().addAll(mapRowTargetChanges.getMapRowTargetsToDelete());
        }
      });
    });
    // Save everything in one go not resource by resource
    mapRowRepository.saveAll(rowChanges.getMapRowsToSave());
    mapRowTargetRepository.saveAll(rowChanges.getMapRowTargetsToSave());
    mapRowTargetRepository.deleteAll(rowChanges.getMapRowTargetsToDelete());
    em.flush();
    em.clear();
    return MappingResponse.builder()
        .rowCount(rowCount.get())
        .updatedRowCount(updatedRowCount.get())
        .build();
  }

  private RowChanges applyMapRowChanges(@NotNull MapRow mapRow, MappingDto mappingUpd,
    int mapRowCount, Task task, User currentUser) {
    MappingDto mappingUpdate = mappingUpd.toBuilder().build();
    RowChanges mapRowChanges = new RowChanges();
    mapRowChanges.setRowChange(RowChange.NO_CHANGE);
    boolean updated = false;
    boolean statusChanged = false;
    boolean statusChange = (mappingUpdate.getStatus() != null);
    if (validChange(mapRow.getStatus(), mappingUpd, task)) {
      // Deal with noMap on MapRow
      if (mappingUpdate.getNoMap() != null) {
        if (mappingUpdate.getNoMap()) {
          mappingUpdate.setStatus(MapStatus.DRAFT);
        } else if (!mappingUpdate.getNoMap() && mapRow.isNoMap()) {
          mappingUpdate.setStatus(MapStatus.UNMAPPED);
        }
        mapRow.setNoMap(mappingUpdate.getNoMap());
        updated = true;
      }
      // Deal with status on MapRow
      if (mappingUpdate.getStatus() != null &&
          (mappingUpdate.getClearTarget() == null || !mappingUpdate.getClearTarget()) &&
          canUpdateStatus(mappingUpdate, mapRowCount, mapRow)) {
        mapRow.setStatus(mappingUpdate.getStatus());
        updated = true;
        // Originally it was a status change and it happened so report it back
        if (statusChange) {
          statusChanged = true;
        }
      }
      // Clear target requested
      if (mappingUpdate.getClearTarget() != null && mappingUpdate.getClearTarget() && !mappingUpdate.isNoMap()) {
        if (!mapRow.isNoMap() && mapRowCount > 0) {
          // Set status to UNMAPPED on the MapRowTarget update will be calculated there
          mapRow.setStatus(MapStatus.UNMAPPED);
        }
      }
      // Rel change requested
      if (mappingUpd.getRelationship() != null) {
        mapRow.setStatus(MapStatus.DRAFT);
      }
      // We don't call the repository event handler to do changes we only need a handful for the bulk edits
      applyAutomaticMapRowUpdates(mapRow, currentUser);
      mapRowChanges.getMapRowsToSave().add(mapRow);

    }
    if (statusChanged) {
      mapRowChanges.setRowChange(RowChange.STATUS_CHANGED);
      return mapRowChanges;
    } else if (updated) {
      mapRowChanges.setRowChange(RowChange.ROW_CHANGED);
    }
    return mapRowChanges;
  }

  private boolean canUpdateStatus(MappingDto mappingUpdate, int mapRowTargetCount, MapRow mapRow) {
    if (mappingUpdate.getStatus() == MapStatus.UNMAPPED && mapRowTargetCount > 0) {
      return false;
    }
    if (mappingUpdate.getStatus() == MapStatus.MAPPED && !mapRow.isNoMap() && mapRowTargetCount == 0) {
      return false;
    }
    if (mappingUpdate.getStatus() == MapStatus.DRAFT && !mapRow.isNoMap() && mapRowTargetCount == 0) {
      return false;
    }
    return mapRow.getStatus().isValidTransition(mappingUpdate.getStatus());
  }

  /**
   * This method is trying to determine if a change can be applied to a row/target
   * <p/>
   * It starts by determining if this is either an author or review task context, or no task which implies this is an owner (only way to get
   * to this code).
   * <p/>
   * If this is an author task then the state must be an author state and the target state must be an author state. If this isn't a state
   * change operations, then all the other changes result in a change to DRAFT/UNMAPPED anyway, so a transition to those states from an
   * author state is fine.
   * <p/>
   * If this is a review task, then the row must be in a review state, and the change can only be a state change to a review (non-author)
   * state.
   * <p/>
   * If there's no task and it is an owner, then they can make any valid state change, and if it isn't a state change operation then all the
   * other changes result in DRAFT/UNMAPPED so test that transition.
   */
  private boolean validChange(MapStatus currentStatus, MappingDto mappingUpd, Task task) {
    if (task != null) {
      if (task.getType().equals(TaskType.AUTHOR) && currentStatus.isAuthorState()) {
        return mappingUpd.getStatus() == null ? true
            : mappingUpd.getStatus().isAuthorState() && currentStatus.isValidTransition(mappingUpd.getStatus());
      } else if (task.getType().equals(TaskType.REVIEW) && mappingUpd.isOnlyStatusChange()) {
        return !mappingUpd.getStatus().isAuthorState() && currentStatus.isValidTransition(mappingUpd.getStatus());
      } else {
        return false;
      }
    } else {
      // no task, must be an owner
      return mappingUpd.isOnlyStatusChange() ? currentStatus.isValidTransition(mappingUpd.getStatus())
          : currentStatus.isValidTransition(MapStatus.DRAFT);
    }
  }

  private RowChanges applyMapRowTargetChanges(MapRowTarget mapRowTarget, MappingDto mappingUpd,
    Task task, RowChange mapRowChange, User currentUser) {
    RowChanges mapRowTargetChanges = new RowChanges();
    mapRowTargetChanges.setRowChange(RowChange.NO_CHANGE);
    if (validChange(mapRowTarget.getRow().getStatus(), mappingUpd, task)) {
      // Clear the target status was already set to UNMAPPED above
      if ((mappingUpd.getClearTarget() != null && mappingUpd.getClearTarget()) || mappingUpd.isNoMap()) {
        mapRowTargetChanges.getMapRowTargetsToDelete().add(mapRowTarget);
        mapRowTargetChanges.setRowChange(RowChange.ROW_CHANGED);
        return mapRowTargetChanges;
      }
      // Changed a relationship
      if (mappingUpd.getRelationship() != null) {
        if (mapRowTarget.getTargetCode() == null) {
          throw new InvalidMappingProblem("invalid-mapping-relationship-no-target", "Cannot set relationship if target is empty");
        }

        mapRowTarget.setRelationship(mappingUpd.getRelationship());
        mapRowTarget.getRow().setStatus(MapStatus.DRAFT);

        // Author is set on maprow already and we don't care about status change so
        // We don't need to call the event handler
        mapRowTargetChanges.getMapRowTargetsToSave().add(mapRowTarget);
        mapRowTargetChanges.setRowChange(RowChange.ROW_CHANGED);
      }
    }
    // Status change happened on mapRow - MapRow Status change nootification happens here as we can have
    // 1 to many mappings and we need a correct updated row count sent back to the user
    if (mapRowChange == RowChange.STATUS_CHANGED) {
      mapRowTargetChanges.setRowChange(RowChange.ROW_CHANGED);
    }
    return mapRowTargetChanges;
  }

  private void applyAutomaticMapRowUpdates(MapRow mapRow, User currentUser) {
    mapRow.setNoMapPrevious(mapRow.isNoMap());
    mapRow.setLastAuthor(currentUser);
  }

  private Task getTask(MappingUpdateDto mappings) {
    Task task = null;

    Set<Long> taskIds = mappings.getMappingDetails().stream().map(MappingDetails::getTaskId).collect(Collectors.toSet());
    if (taskIds.size() != 1) {
      throw new InvalidBulkChangeProblem("Specified task identifiers for bulk changes must be the same value");
    }

    Long taskId = taskIds.iterator().next();
    if (taskId != null) {
      task = taskRepository.findById(taskId)
          .orElseThrow(() -> new InvalidMappingProblem("Task", taskId));
    }

    return task;
  }

  private boolean rowIsAssignedToTask(Task task, MapRow mapRow) {
    switch (task.getType()) {
      case AUTHOR:
        return mapRow.getAuthorTask() != null && mapRow.getAuthorTask().getId().equals(task.getId());
      case REVIEW:
        return mapRow.getReviewTask() != null && mapRow.getReviewTask().getId().equals(task.getId());
      default:
        throw new IllegalArgumentException("Unknown task type " + task.getType());
    }
  }

  @Transactional
  public Long newMappingVersion(Long mapId, Long newSourceId, String mapVersion, String targetVersion, String targetScope) throws IOException {
    final Map originalMap = mapRepository.findById(mapId)
        .orElseThrow(() -> Problem.valueOf(Status.NOT_FOUND, "No Map found with id " + mapId));
    final ImportedCodeSet newSource = importedCodeSetRepository.findById(newSourceId)
        .orElseThrow(() -> Problem.valueOf(Status.NOT_FOUND, "No ImportedSourceCode found with id " + newSourceId));
    final Long projectId = originalMap.getProject().getId();

    if (!mapRepository.findAllByProjectIdAndVersion(projectId, mapVersion).isEmpty()) {
      throw Problem.builder().withStatus(Status.BAD_REQUEST)
          .withType(URI.create(Snap2SnomedProblem.BASE_PROBLEM_TYPE_URI + "new-map-version/duplicate-version"))
          .withTitle("Map already exists with specified version")
          .withDetail("A map already exists with version " + mapVersion + " in project " + projectId).build();
    }

    final User currentUser = authenticationFacade.getAuthenticatedUser();
    final String userId = currentUser.getId();
    final Instant dateTime = Instant.now();

    final long originalSourceId = originalMap.getSource().getId();

    Map newMap = Map.builder()
        .project(originalMap.getProject())
        .mapVersion(mapVersion)
        .toVersion(targetVersion)
        .toScope(targetScope)
        .source(newSource)
        .build();

    final Map mapCreated = mapRepository.save(newMap);
    final Long createdId = mapCreated.getId();

    if (newSourceId == originalSourceId) {
      // Copy all rows
      mapRowRepository.copyMapRows(createdId, mapId, userId, dateTime);
      mapRowTargetRepository.copyMapRowTargets(createdId, mapId, userId, dateTime);
    } else {
      // Copy all rows where code in new Source
      mapRowRepository.copyMapRowsForNewSource(createdId, mapId, userId, dateTime, newSourceId);
      // Copy existing targets
      mapRowTargetRepository.copyMapRowTargetsForNewSource(createdId, mapId, userId, dateTime);
      // Add new rows where previously non-existing
      mapRowRepository.createMapRowsForNewSource(createdId, userId, dateTime, newSourceId);
    }

    validateMapTargets(createdId);

    return createdId;
  }

  @Transactional
  public ValidationResult validateMapTargets(Long mapId) throws IOException {
    final User currentUser = authenticationFacade.getAuthenticatedUser();
    final String userId = currentUser.getId();
    final List<MapRowTarget> mapRowTargets = mapRowTargetRepository.findByMapId(mapId);
    Set<String> targetCodes = mapRowTargets.stream().map(target -> target.getTargetCode()).collect(Collectors.toSet());
    String scope = null;
    String csVersion = null;
    Optional<org.snomed.snap2snomed.model.Map> map = mapRepository.findById(Long.valueOf(mapId));
    if (map.isPresent()) {
      csVersion = map.get().getToVersion();
      scope = map.get().getToScope();
    }
    ValidationResult validationResult = fhirService.validateValueSetComposition(targetCodes, csVersion, scope);
    log.info(MessageFormat.format("The target code system returned the following validation, for the {0} provided codes: {1}",
            targetCodes.size(), validationResult.toString()));
    Set<String> targetsToFlag = new HashSet<String>(validationResult.getInactive());
    targetsToFlag.addAll(validationResult.getAbsent());
    targetsToFlag.addAll(validationResult.getInvalid());
    if (!targetsToFlag.isEmpty()) {
      log.info("About to flag the following target codes, for map " + mapId + " {user: " + userId + ", " +
              "modified: " + Instant.now() + "}: " + String.join(",", targetsToFlag));
      List<Long> targetIds = mapRowTargetRepository.findByMapId(mapId)
              .stream()
              .filter(target -> targetsToFlag.contains(target.getTargetCode()))
              .map(MapRowTarget::getId)
              .collect(Collectors.toList());
      mapRowTargetRepository.flagMapTargets(targetIds, userId, Instant.now());
    }
    return validationResult;
  }

}
