import * as jwt from 'jsonwebtoken';
import jwksClient from 'jwks-rsa';
import { OAuth2Client } from 'google-auth-library';

const appleJwks = jwksClient({
  jwksUri: 'https://appleid.apple.com/auth/keys',
  cache: true,
  cacheMaxAge: 86_400_000,
  rateLimit: true,
});

function appleSigningKey(
  header: jwt.JwtHeader,
  callback: jwt.SigningKeyCallback,
): void {
  if (!header.kid) {
    callback(new Error('Apple token missing kid'));
    return;
  }
  appleJwks.getSigningKey(header.kid, (err, key) => {
    if (err) {
      callback(err);
      return;
    }
    const signingKey = key?.getPublicKey();
    if (!signingKey) {
      callback(new Error('Apple signing key missing'));
      return;
    }
    callback(null, signingKey);
  });
}

export type AppleIdTokenPayload = jwt.JwtPayload & {
  sub: string;
  email?: string;
};

export async function verifyAppleIdentityToken(
  idToken: string,
  allowedAudiences: string[],
): Promise<AppleIdTokenPayload> {
  if (allowedAudiences.length === 0) {
    throw new Error('Apple Sign In is not configured (no audiences)');
  }
  return new Promise((resolve, reject) => {
    jwt.verify(
      idToken,
      appleSigningKey,
      {
        algorithms: ['RS256'],
        issuer: 'https://appleid.apple.com',
      },
      (err, decoded) => {
        if (err) {
          reject(err);
          return;
        }
        const payload = decoded as jwt.JwtPayload;
        const aud = payload.aud;
        const list =
          aud == null
            ? []
            : Array.isArray(aud)
              ? aud.map(String)
              : [String(aud)];
        if (!allowedAudiences.some((a) => list.includes(a))) {
          reject(new Error('Invalid Apple audience'));
          return;
        }
        const sub = payload.sub;
        if (!sub || typeof sub !== 'string') {
          reject(new Error('Apple token missing sub'));
          return;
        }
        resolve(payload as AppleIdTokenPayload);
      },
    );
  });
}

export type GoogleIdTokenPayload = {
  sub: string;
  email?: string;
  email_verified?: boolean | string;
  given_name?: string;
  family_name?: string;
};

const googleOAuthClient = new OAuth2Client();

export async function verifyGoogleIdentityToken(
  idToken: string,
  allowedAudiences: string[],
): Promise<GoogleIdTokenPayload> {
  if (allowedAudiences.length === 0) {
    throw new Error('Google Sign In is not configured (no audiences)');
  }
  const ticket = await googleOAuthClient.verifyIdToken({
    idToken,
    audience: allowedAudiences.length === 1 ? allowedAudiences[0] : allowedAudiences,
  });
  const payload = ticket.getPayload();
  if (!payload?.sub) {
    throw new Error('Google token missing sub');
  }
  return payload as GoogleIdTokenPayload;
}
