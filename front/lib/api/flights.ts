import { Flight } from "@/lib/simulation-data"
import { apiClient } from "./client"

export const flightsApi = {
  getAll: () => apiClient.get<Flight[]>("/flights"),
  getById: (id: string) => apiClient.get<Flight>(`/flights/${id}`),
}
