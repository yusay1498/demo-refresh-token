import { StrictMode } from "react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { AuthProvider, type AuthSession } from "./AuthContext";
import { AuthGate } from "./AuthGate";
import SignInPage from "../app/signin/page";

const navigation = vi.hoisted(() => ({ pathname: "/demo", replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => navigation.pathname,
  useRouter: () => ({ replace: navigation.replace }),
}));

const savedSession: AuthSession = {
  token: "access-token",
  refreshToken: "refresh-token",
  username: "demo-user",
  authorities: ["ROLE_USER"],
  tokenType: "Bearer",
  expiresIn: 900,
};
const sessionKey = "demo-refresh-token.auth";
const fetchMock = vi.fn<typeof fetch>();
const userInfo = { username: savedSession.username, authorities: savedSession.authorities };

function renderRoute(signIn = false) {
  navigation.pathname = signIn ? "/signin" : "/demo";
  return render(
    <StrictMode>
      <AuthProvider>
        <AuthGate>{signIn ? <SignInPage /> : <h1>Protected content</h1>}</AuthGate>
      </AuthProvider>
    </StrictMode>,
  );
}

beforeEach(() => {
  window.sessionStorage.clear();
  navigation.replace.mockReset();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("never renders protected content while checking or redirecting an anonymous visitor", async () => {
  let resolvePublic!: (response: Response) => void;
  fetchMock.mockImplementationOnce(() => new Promise(resolve => { resolvePublic = resolve; }));
  renderRoute();
  expect(screen.queryByText("Protected content")).toBeNull();
  expect(screen.getByRole("status").textContent).toBe("認証状態を確認中...");
  await act(async () => resolvePublic(new Response("anonymous")));
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/signin"));
  expect(screen.queryByText("Protected content")).toBeNull();
});

it("renders protected content only after saved session verification succeeds", async () => {
  window.sessionStorage.setItem(sessionKey, JSON.stringify(savedSession));
  let resolveUser!: (response: Response) => void;
  fetchMock.mockImplementationOnce(() => new Promise(resolve => { resolveUser = resolve; }));
  renderRoute();
  expect(screen.queryByText("Protected content")).toBeNull();
  await act(async () => resolveUser(Response.json(userInfo)));
  await screen.findByText("Protected content");
  expect(navigation.replace).not.toHaveBeenCalled();
});

it.each(["offline", "503"])("hides protected content on %s and allows recovery without userInfo", async failure => {
  window.sessionStorage.setItem(sessionKey, JSON.stringify(savedSession));
  if (failure === "offline") fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
  else fetchMock.mockResolvedValueOnce(new Response(null, { status: 503 }));
  renderRoute();
  await screen.findByText("接続を確認できません");
  expect(screen.queryByText("Protected content")).toBeNull();
  expect(screen.getByRole("button", { name: "ログアウト" })).toBeTruthy();
  expect(navigation.replace).not.toHaveBeenCalled();
  fetchMock.mockResolvedValueOnce(Response.json(userInfo));
  fireEvent.click(screen.getByRole("button", { name: "認証APIを再確認" }));
  await screen.findByText("Protected content");
});

it("redirects to signIn after logout from the recovery screen", async () => {
  window.sessionStorage.setItem(sessionKey, JSON.stringify(savedSession));
  fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
  renderRoute();
  await screen.findByText("接続を確認できません");
  fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
  fireEvent.click(screen.getByRole("button", { name: "ログアウト" }));
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/signin"));
  expect(screen.queryByText("Protected content")).toBeNull();
  expect(window.sessionStorage.getItem(sessionKey)).toBeNull();
});

it("redirects to signIn when stored credentials are rejected", async () => {
  window.sessionStorage.setItem(sessionKey, JSON.stringify(savedSession));
  fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
  fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
  renderRoute();
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/signin"));
  expect(screen.queryByText("Protected content")).toBeNull();
});

it("keeps the public signIn page accessible when the API is offline", async () => {
  fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
  renderRoute(true);
  await screen.findByLabelText("パスワード");
  expect(screen.getByRole("button", { name: "サインイン" })).toBeTruthy();
  expect(navigation.replace).not.toHaveBeenCalled();
});

it("redirects an offline visitor without a session away from protected content", async () => {
  fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
  renderRoute();
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/signin"));
  expect(screen.queryByText("Protected content")).toBeNull();
});

it("signs in from the public page then redirects to the protected home", async () => {
  fetchMock.mockResolvedValueOnce(new Response("anonymous"));
  renderRoute(true);
  await screen.findByLabelText("パスワード");
  fetchMock.mockResolvedValueOnce(Response.json(savedSession));
  fetchMock.mockResolvedValueOnce(Response.json(userInfo));
  fireEvent.change(screen.getByLabelText("ユーザー名"), { target: { value: "demo-user" } });
  fireEvent.change(screen.getByLabelText("パスワード"), { target: { value: "test-password" } });
  fireEvent.click(screen.getByRole("button", { name: "サインイン" }));
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/demo"));
  expect(screen.queryByLabelText("パスワード")).toBeNull();
});

it("shows a failed signIn without granting access", async () => {
  fetchMock.mockResolvedValueOnce(new Response("anonymous"));
  renderRoute(true);
  await screen.findByLabelText("パスワード");
  fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
  fireEvent.change(screen.getByLabelText("ユーザー名"), { target: { value: "demo-user" } });
  fireEvent.change(screen.getByLabelText("パスワード"), { target: { value: "wrong-password" } });
  fireEvent.click(screen.getByRole("button", { name: "サインイン" }));
  await screen.findByRole("alert");
  expect(navigation.replace).not.toHaveBeenCalled();
  expect(window.sessionStorage.getItem(sessionKey)).toBeNull();
});
