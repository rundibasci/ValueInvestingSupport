import { useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { thesisApi } from "../api/thesis";
import { useAuth } from "../auth/AuthProvider";

function formatDate(value: string | null | undefined): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("en-US", { month: "short", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function classificationLabel(value: string | null | undefined): string {
  return (value || "INSUFFICIENT_DATA").replace(/_/g, " ").toLowerCase();
}

export function AdminThesisReviewPage(): JSX.Element {
  const { session } = useAuth();
  const [page, setPage] = useState(0);

  const queue = useQuery({
    queryKey: ["thesis-review-queue", page],
    queryFn: () => thesisApi.reviewQueue(page),
  });

  if (session?.role !== "ADMIN") return <Navigate to="/" replace />;

  return (
    <section className="space-y-6">
      <div>
        <p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">Operations</p>
        <h1 className="mt-2 text-3xl font-semibold text-white">AI Thesis Review Queue</h1>
        <p className="mt-3 max-w-3xl leading-7 text-slate-300">
          Every AI-generated investment thesis flagged for human review or carrying a data-quality warning, across every symbol.
        </p>
      </div>

      {queue.isPending && <p className="text-sm text-slate-400">Loading review queue…</p>}
      {queue.error && (
        <p role="alert" className="rounded-lg border border-rose-300/30 bg-rose-400/10 p-4 text-sm text-rose-100">
          {queue.error instanceof Error ? queue.error.message : "The review queue is unavailable."}
        </p>
      )}

      {queue.data && queue.data.content.length === 0 && (
        <p className="rounded-lg border border-slate-700 bg-slate-900/60 p-4 text-sm text-slate-300">
          No theses are currently pending human review.
        </p>
      )}

      {queue.data && queue.data.content.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-slate-800">
          <table className="w-full min-w-[720px] text-left text-sm text-slate-200">
            <thead className="bg-slate-900/80 text-xs uppercase tracking-wide text-slate-400">
              <tr>
                <th className="px-4 py-3">Symbol</th>
                <th className="px-4 py-3">Company</th>
                <th className="px-4 py-3">Classification</th>
                <th className="px-4 py-3">Human review</th>
                <th className="px-4 py-3">Data warnings</th>
                <th className="px-4 py-3">Generated at</th>
              </tr>
            </thead>
            <tbody>
              {queue.data.content.map((item) => (
                <tr key={item.id} className="border-t border-slate-800">
                  <td className="px-4 py-3 font-semibold">
                    <Link to={`/securities/${item.symbol}/review#thesis`} className="text-emerald-300 underline">{item.symbol}</Link>
                  </td>
                  <td className="px-4 py-3 text-slate-400">{item.companyName ?? "-"}</td>
                  <td className="px-4 py-3">{classificationLabel(item.classification)}</td>
                  <td className="px-4 py-3">
                    {item.humanReviewRequired ? (
                      <span className="inline-flex rounded-full bg-rose-400/15 px-2.5 py-1 text-xs font-semibold text-rose-100">flagged</span>
                    ) : (
                      <span className="text-slate-500">no</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {item.dataWarningsPresent ? (
                      <span className="inline-flex rounded-full bg-amber-300/15 px-2.5 py-1 text-xs font-semibold text-amber-100">present</span>
                    ) : (
                      <span className="text-slate-500">none</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-slate-400">{formatDate(item.generatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {queue.data && queue.data.totalPages > 1 && (
        <div className="flex items-center justify-end gap-3 text-sm">
          <button
            type="button"
            onClick={() => setPage((current) => Math.max(0, current - 1))}
            disabled={page === 0}
            className="rounded-lg border border-slate-700 px-3 py-1.5 font-semibold text-slate-200 disabled:opacity-50"
          >
            Previous
          </button>
          <span className="text-slate-400">Page {page + 1} of {queue.data.totalPages}</span>
          <button
            type="button"
            onClick={() => setPage((current) => Math.min(queue.data!.totalPages - 1, current + 1))}
            disabled={page + 1 >= queue.data.totalPages}
            className="rounded-lg border border-slate-700 px-3 py-1.5 font-semibold text-slate-200 disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}
    </section>
  );
}
