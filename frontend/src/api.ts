export type PendingCommand = {key: string; path: string; body: string; label: string};
const storageKey = (username: string) => 'proco.pending.' + username;

export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

export async function read<T>(path: string, credentials: string): Promise<T> {
  const response = await fetch('/api' + path, {headers: {Authorization: 'Basic ' + credentials}});
  if (!response.ok) throw await errorFor(response);
  return response.json();
}

async function errorFor(response: Response): Promise<ApiError> {
  let message = response.status === 401 ? 'Usuario o contraseña incorrectos' : 'No fue posible completar la operación';
  try { message = (await response.json()).message || message; } catch { /* Status is still definitive. */ }
  return new ApiError(message, response.status);
}

export function pendingFor(username: string): PendingCommand | null {
  const raw = sessionStorage.getItem(storageKey(username));
  if (!raw) return null;
  const value: unknown = JSON.parse(raw);
  if (!value || typeof value !== 'object' || !('key' in value) || !('path' in value)
      || !('body' in value) || !('label' in value)
      || ![value.key, value.path, value.body, value.label].every(v => typeof v === 'string')) {
    throw new Error('No se puede leer el comando pendiente de esta sesión; no se enviará un comando nuevo.');
  }
  return value as PendingCommand;
}

export async function submit<T>(username: string, credentials: string, path: string, body: unknown, label: string): Promise<T> {
  if (pendingFor(username)) throw new Error('Confirma primero el comando incierto mediante el botón de reintento.');
  const command = {key: crypto.randomUUID(), path, body: JSON.stringify(body), label};
  // Persist BEFORE sending; storage errors prevent an untracked mutation.
  sessionStorage.setItem(storageKey(username), JSON.stringify(command));
  return send<T>(username, credentials, command);
}

export async function retry<T>(username: string, credentials: string): Promise<T> {
  const command = pendingFor(username);
  if (!command) throw new Error('No existe un comando pendiente para esta identidad.');
  return send<T>(username, credentials, command);
}

async function send<T>(username: string, credentials: string, command: PendingCommand): Promise<T> {
  const abort = new AbortController();
  const timeout = window.setTimeout(() => abort.abort(), 15000);
  try {
    const response = await fetch('/api' + command.path, {
      method: 'POST', signal: abort.signal, body: command.body,
      headers: {Authorization: 'Basic ' + credentials, 'Content-Type': 'application/json', 'Idempotency-Key': command.key},
    });
    if (!response.ok) {
      // An auth/proxy timeout on a retry cannot establish whether an earlier attempt committed.
      if (response.status >= 400 && response.status < 500 && ![401,408,425,429].includes(response.status)) {
        sessionStorage.removeItem(storageKey(username));
      }
      throw await errorFor(response);
    }
    const result: T = await response.json();
    if (!result || typeof result !== 'object' || !('id' in result) || typeof result.id !== 'string'
        || !('version' in result) || !Number.isSafeInteger(result.version) || Number(result.version) < 0) {
      throw new Error('Respuesta de comando incompleta; conserva la clave y reintenta para confirmar el resultado.');
    }
    sessionStorage.removeItem(storageKey(username));
    return result;
  } finally {
    window.clearTimeout(timeout);
    // Network, 5xx and unreadable success responses deliberately leave the frozen command in storage.
  }
}
