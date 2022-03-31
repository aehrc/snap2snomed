package org.snomed.snap2snomed.repository.dto;

import org.snomed.snap2snomed.model.MapRow;
import org.snomed.snap2snomed.model.MapRowTarget;
import lombok.Data;

@Data
public class MapRowTargetsForMapRowDto {
  private MapRow mapRow;
  private MapRowTarget mapRowTarget;

  public MapRowTargetsForMapRowDto(MapRow mapRow, MapRowTarget mapRowTarget) {
    this.mapRow = mapRow;
    this.mapRowTarget = mapRowTarget;
  }

}
