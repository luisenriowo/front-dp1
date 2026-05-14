import { Airport } from "@/lib/simulation-data"
import { apiClient } from "./client"

function toAirport(raw: any): Airport {
  return { ...raw, coordinates: [raw.longitude, raw.latitude] as [number, number] }
}

export const airportsApi = {
  getAll: () => apiClient.get<any[]>("/airports").then(list => list.map(toAirport)),
  getById: (id: string) => apiClient.get<any>(`/airports/${id}`).then(toAirport),
}
