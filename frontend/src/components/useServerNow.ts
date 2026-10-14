import { useEffect, useState } from 'react';

/** Current time on the server's clock, re-rendering every quarter second. */
export function useServerNow(clockOffset: number): number {
  const [now, setNow] = useState(() => Date.now() + clockOffset);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now() + clockOffset), 250);
    return () => clearInterval(timer);
  }, [clockOffset]);
  return now;
}

export function secondsUntil(deadline: number, now: number): number {
  return Math.max(0, Math.ceil((deadline - now) / 1000));
}
