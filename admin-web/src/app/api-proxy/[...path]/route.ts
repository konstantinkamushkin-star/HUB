import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function backendOrigin(): string {
  const raw =
    process.env.BACKEND_URL ||
    process.env.NEXT_PUBLIC_API_URL ||
    "https://api.dive-hub.ru";
  return raw.replace(/\/$/, "");
}

function stripHopByHopHeaders(h: Headers): Headers {
  const out = new Headers(h);
  out.delete("host");
  out.delete("connection");
  out.delete("content-length");
  return out;
}

async function proxy(
  req: NextRequest,
  pathSegments: string[],
  method: string,
): Promise<NextResponse> {
  const base = backendOrigin();
  const sub = pathSegments.length ? pathSegments.join("/") : "";
  const url = `${base}/api/${sub}${req.nextUrl.search}`;

  const headers = stripHopByHopHeaders(req.headers);
  const auth = req.headers.get("authorization");
  if (auth) {
    headers.set("Authorization", auth);
  }

  let body: ArrayBuffer | undefined;
  if (!["GET", "HEAD"].includes(method)) {
    const buf = await req.arrayBuffer();
    body = buf.byteLength ? buf : undefined;
  }

  try {
    const res = await fetch(url, {
      method,
      headers,
      body: body as BodyInit | undefined,
      cache: "no-store",
    });
    const buf = await res.arrayBuffer();
    const outHeaders = new Headers(res.headers);
    outHeaders.delete("content-encoding");
    outHeaders.delete("content-length");
    return new NextResponse(buf, {
      status: res.status,
      statusText: res.statusText,
      headers: outHeaders,
    });
  } catch (err) {
    const m = err instanceof Error ? err.message : String(err);
    return NextResponse.json(
      {
        statusCode: 502,
        message: `Не удалось достучаться до API (${base}). Запустите backend: cd backend && npm run start:dev. ${m}`,
      },
      { status: 502 },
    );
  }
}

type Ctx = { params: Promise<{ path?: string[] }> };

export async function GET(req: NextRequest, ctx: Ctx) {
  const { path = [] } = await ctx.params;
  return proxy(req, path, "GET");
}

export async function POST(req: NextRequest, ctx: Ctx) {
  const { path = [] } = await ctx.params;
  return proxy(req, path, "POST");
}

export async function PUT(req: NextRequest, ctx: Ctx) {
  const { path = [] } = await ctx.params;
  return proxy(req, path, "PUT");
}

export async function PATCH(req: NextRequest, ctx: Ctx) {
  const { path = [] } = await ctx.params;
  return proxy(req, path, "PATCH");
}

export async function DELETE(req: NextRequest, ctx: Ctx) {
  const { path = [] } = await ctx.params;
  return proxy(req, path, "DELETE");
}

export async function OPTIONS(req: NextRequest, ctx: Ctx) {
  const { path = [] } = await ctx.params;
  return proxy(req, path, "OPTIONS");
}
