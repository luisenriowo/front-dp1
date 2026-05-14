import { Airport } from "@/lib/simulation-data"
import { apiClient } from "./client"

function toContinent(raw: string): Airport["continent"] {
  const value = String(raw).toUpperCase()
  if (value === "AMERICA") return "America"
  if (value === "EUROPE") return "Europe"
  return "Asia"
}

export function toAirport(raw: any): Airport {
  return {
    ...raw,
    continent: toContinent(raw.continent),
    coordinates: [raw.longitude, raw.latitude] as [number, number],
  }
}

export const airportsApi = {
  getAll: () => apiClient.get<any[]>("/airports").then(list => list.map(toAirport)),
  getById: (id: string) => apiClient.get<any>(`/airports/${id}`).then(toAirport),
}
