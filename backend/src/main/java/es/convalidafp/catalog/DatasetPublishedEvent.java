package es.convalidafp.catalog;

/**
 * Se ha publicado una versión de dataset (un borrador nuevo o una versión archivada restaurada).
 * Quien guarde datos publicados en memoria debe descartarlos al recibirlo.
 */
public record DatasetPublishedEvent(Long versionId, String academicYear) {}
