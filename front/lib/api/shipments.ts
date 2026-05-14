import { Shipment } from "@/lib/simulation-data"
import { apiClient } from "./client"

export type CreateShipmentDto = {
  origin: string
  destination: string
  quantity: number
  deadline: string // ISO 8601
}

export const shipmentsApi = {
  getAll: () => apiClient.get<Shipment[]>("/shipments"),
  getById: (id: string) => apiClient.get<Shipment>(`/shipments/${id}`),
  create: (dto: CreateShipmentDto) => apiClient.post<Shipment>("/shipments", dto),
  // Crea múltiples envíos en paralelo; devuelve solo los que tuvieron éxito
  createBulk: async (dtos: CreateShipmentDto[]): Promise<Shipment[]> => {
    const results = await Promise.allSettled(
      dtos.map((dto) => apiClient.post<Shipment>("/shipments", dto))
    )
    return results
      .filter((r): r is PromiseFulfilledResult<Shipment> => r.status === "fulfilled")
      .map((r) => r.value)
  },
}
