import { request, withQuery } from './client'
import type {
  Comparison,
  Cycle,
  DatasetResponse,
  Degree,
  Explore,
  Simulation,
  SourceDocument,
  University,
} from './types'

const BASE = '/api/v1'

export function getDataset(year?: string): Promise<DatasetResponse> {
  return request(withQuery(`${BASE}/dataset`, { year }))
}

export function getCycles(year?: string): Promise<Cycle[]> {
  return request(withQuery(`${BASE}/cycles`, { year }))
}

export function getUniversities(year?: string): Promise<University[]> {
  return request(withQuery(`${BASE}/universities`, { year }))
}

/** Con `cycle`, solo los grados con fila para ese ciclo, con su `tableStatus`. */
export function getDegrees(filter: { university?: string; cycle?: string; year?: string } = {}): Promise<Degree[]> {
  return request(withQuery(`${BASE}/degrees`, filter))
}

export function getSources(year?: string): Promise<SourceDocument[]> {
  return request(withQuery(`${BASE}/sources`, { year }))
}

export function simulate(cycle: string, degree: string, year?: string): Promise<Simulation> {
  return request(withQuery(`${BASE}/simulations`, { cycle, degree, year }))
}

/** Dirección del informe PDF de una simulación (se descarga con un enlace normal). */
export function reportUrl(cycle: string, degree: string, year?: string): string {
  return withQuery(`${BASE}/simulations/report.pdf`, { cycle, degree, year })
}

export function compare(cycle: string, degrees: string[], year?: string): Promise<Comparison> {
  return request(withQuery(`${BASE}/comparisons`, { cycle, degrees: degrees.join(','), year }))
}

export function explore(cycle: string, year?: string): Promise<Explore> {
  return request(withQuery(`${BASE}/explore`, { cycle, year }))
}
