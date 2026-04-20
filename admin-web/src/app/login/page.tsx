import { redirect } from "next/navigation";

/** Публичный `/login` отключён — используйте скрытый путь из `lib/adminLoginPath.ts` (или env). */
export default function LegacyLoginRedirect() {
  redirect("/");
}
