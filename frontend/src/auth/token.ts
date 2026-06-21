let accessToken: string | null = null
let unauthorizedHandler: (() => Promise<boolean>) | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

export function setUnauthorizedHandler(handler: (() => Promise<boolean>) | null): void {
  unauthorizedHandler = handler
}

export async function refreshAfterUnauthorized(): Promise<boolean> {
  return unauthorizedHandler ? unauthorizedHandler() : false
}
