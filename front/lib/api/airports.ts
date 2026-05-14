import { Airport } from "@/lib/simulation-data"
import { apiClient } from "./client"

export const airportsApi = {
  getAll: () => apiClient.get<Airport[]>("/airports"),
  getById: (id: string) => apiClient.get<Airport>(`/airports/${id}`),
}
