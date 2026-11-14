package es.convalidafp.catalog;

import java.util.List;

public interface DegreeRepository extends VersionedRepository<Degree> {

  List<Degree> findByUniversityIdOrderByCode(Long universityId);
}
