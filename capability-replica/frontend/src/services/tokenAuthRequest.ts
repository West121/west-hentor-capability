export interface TokenAuthCredentials {
  userName?: string;
  userNameOrEmailAddress?: string;
  password?: string;
  twoFactorVerificationCode?: string;
  rememberClient?: boolean;
}

export interface TokenAuthAuthenticatePayload {
  userNameOrEmailAddress?: string;
  password?: string;
  twoFactorVerificationCode?: string;
  rememberClient?: boolean;
}

export function tokenAuthAuthenticatePayload(credentials?: TokenAuthCredentials): TokenAuthAuthenticatePayload | undefined {
  if (!credentials) {
    return undefined;
  }
  return {
    userNameOrEmailAddress: credentials.userNameOrEmailAddress ?? credentials.userName,
    password: encodeTokenAuthPassword(credentials.password),
    twoFactorVerificationCode: credentials.twoFactorVerificationCode,
    rememberClient: credentials.rememberClient,
  };
}

export function encodeTokenAuthPassword(password?: string) {
  if (password === undefined) {
    return undefined;
  }
  const bytes = new TextEncoder().encode(password);
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return globalThis.btoa(binary);
}
