package es.convalidafp.catalog;

import java.util.List;

public interface DegreeSubjectRepository extends VersionedRepository<DegreeSubject> {

  List<DegreeSubject> findByDegreeIdOrderByCourseYearAscCodeAsc(Long degreeId);
}
