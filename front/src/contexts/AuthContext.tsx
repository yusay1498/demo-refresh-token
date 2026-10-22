"use client";

import { createContext, ReactNode, useContext, useEffect, useRef, useState } from "react";

export type AuthSession = {
  token: string;
  username: string;
  authorities: string[];
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
};

export type UserInfo = {
  username: string;
  authorities: string[];
};

export type AuthState = "checking" | "signingIn" | "anonymous" | "authenticated" | "unavailable";

type AuthContextValue = {
  authState: AuthState;
  session: AuthSession | null;
  userInfo: UserInfo | null;
  publicResult: string;
  busy: boolean;
  signIn: (username: string, password: string) => Promise<void>;
  checkPublicEndpoint: () => Promise<void>;
  checkProtectedEndpoint: () => Promise<void>;
  signOut: (revokeAll: boolean) => Promise<void>;
};

class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

const API_BASE_URI = (process.env.NEXT_PUBLIC_API_BASE_URI || "http://localhost:8080").replace(/\/$/, "");
const SESSION_KEY = "demo-refresh-token.auth";
const AuthContext = createContext<AuthContextValue | null>(null);

async function checkedResponse(response: Response) {
  if (response.ok) return response;
  throw new ApiError((await response.text()) || `API request failed (${response.status})`, response.status);
}

async function readUserInfo(token: string) {
  return fetch(`${API_BASE_URI}/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authState, setAuthState] = useState<AuthState>("checking");
  const [session, setSession] = useState<AuthSession | null>(null);
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const [publicResult, setPublicResult] = useState("");
  const [busy, setBusy] = useState(false);
  const restoreStarted = useRef(false);

  function storeSession(nextSession: AuthSession | null) {
    setSession(nextSession);
    if (nextSession) window.sessionStorage.setItem(SESSION_KEY, JSON.stringify(nextSession));
    else window.sessionStorage.removeItem(SESSION_KEY);
  }

  async function refreshSession(currentSession: AuthSession) {
    const response = await checkedResponse(await fetch(`${API_BASE_URI}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: currentSession.refreshToken }),
    }));
    const refreshedSession = (await response.json()) as AuthSession;
    storeSession(refreshedSession);
    return refreshedSession;
  }

  async function verifySession(currentSession: AuthSession) {
    let activeSession = currentSession;
    let response = await readUserInfo(activeSession.token);
    if (response.status === 401) {
      activeSession = await refreshSession(activeSession);
      response = await readUserInfo(activeSession.token);
    }
    await checkedResponse(response);
    setUserInfo((await response.json()) as UserInfo);
    storeSession(activeSession);
    setAuthState("authenticated");
  }

  async function checkPublicEndpoint() {
    const response = await checkedResponse(await fetch(`${API_BASE_URI}/auth/anon`));
    setPublicResult(await response.text());
  }

  async function signIn(username: string, password: string) {
    setBusy(true);
    setAuthState("signingIn");
    try {
      const response = await checkedResponse(await fetch(`${API_BASE_URI}/auth/signIn`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      }));
      const signedInSession = (await response.json()) as AuthSession;
      storeSession(signedInSession);
      await verifySession(signedInSession);
    } catch (error) {
      storeSession(null);
      setUserInfo(null);
      setAuthState("anonymous");
      throw error;
    } finally {
      setBusy(false);
    }
  }

  async function checkProtectedEndpoint() {
    if (!session) return;
    setBusy(true);
    try {
      await verifySession(session);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        storeSession(null);
        setUserInfo(null);
        setAuthState("anonymous");
      } else {
        setAuthState("unavailable");
      }
      throw error;
    } finally {
      setBusy(false);
    }
  }

  async function signOut(revokeAll: boolean) {
    if (!session) return;
    setBusy(true);
    try {
      let response = revokeAll
        ? await fetch(`${API_BASE_URI}/auth/signOutAll`, {
            method: "POST",
            headers: { Authorization: `Bearer ${session.token}` },
          })
        : await fetch(`${API_BASE_URI}/auth/signOut`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken: session.refreshToken }),
          });
      if (revokeAll && response.status === 401) {
        const refreshedSession = await refreshSession(session);
        response = await fetch(`${API_BASE_URI}/auth/signOutAll`, {
          method: "POST",
          headers: { Authorization: `Bearer ${refreshedSession.token}` },
        });
      }
      await checkedResponse(response);
      storeSession(null);
      setUserInfo(null);
      setAuthState("anonymous");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (restoreStarted.current) return;
    restoreStarted.current = true;

    async function restoreSession() {
      const saved = window.sessionStorage.getItem(SESSION_KEY);
      if (!saved) {
        try {
          const response = await checkedResponse(await fetch(`${API_BASE_URI}/auth/anon`));
          const result = await response.text();
          setPublicResult(result);
          setAuthState(current => current === "checking" ? "anonymous" : current);
        } catch {
          setAuthState(current => current === "checking" ? "unavailable" : current);
        }
        return;
      }

      let savedSession: AuthSession;
      try {
        const parsed: unknown = JSON.parse(saved);
        if (typeof parsed !== "object" || parsed === null
          || !("token" in parsed) || typeof parsed.token !== "string" || !parsed.token
          || !("refreshToken" in parsed) || typeof parsed.refreshToken !== "string" || !parsed.refreshToken
          || !("username" in parsed) || typeof parsed.username !== "string"
          || !("authorities" in parsed) || !Array.isArray(parsed.authorities)
          || !parsed.authorities.every(authority => typeof authority === "string")
          || !("tokenType" in parsed) || typeof parsed.tokenType !== "string"
          || !("expiresIn" in parsed) || typeof parsed.expiresIn !== "number") {
          throw new Error("Invalid session");
        }
        savedSession = parsed as AuthSession;
      } catch {
        storeSession(null);
        setUserInfo(null);
        setAuthState("anonymous");
        return;
      }

      setSession(savedSession);
      try {
        await verifySession(savedSession);
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          storeSession(null);
          setUserInfo(null);
          setAuthState("anonymous");
        } else {
          setAuthState("unavailable");
        }
      }
    }

    void restoreSession();
    // Session restoration runs once when the provider mounts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <AuthContext.Provider value={{
      authState,
      session,
      userInfo,
      publicResult,
      busy,
      signIn,
      checkPublicEndpoint,
      checkProtectedEndpoint,
      signOut,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within an AuthProvider");
  return context;
}
