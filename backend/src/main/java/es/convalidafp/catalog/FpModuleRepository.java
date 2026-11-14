package es.convalidafp.catalog;

import java.util.List;

public interface FpModuleRepository extends VersionedRepository<FpModule> {

  List<FpModule> findByCycleIdOrderByCode(Long cycleId);
}
