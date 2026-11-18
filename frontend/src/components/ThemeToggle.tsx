import { useEffect, useState } from 'react';

type Theme = 'light' | 'dark';

const darkQuery = '(prefers-color-scheme: dark)';

function systemTheme(): Theme {
  return window.matchMedia(darkQuery).matches ? 'dark' : 'light';
}

/** The theme the reader picked earlier, if any. Storage can be unavailable, so failures fall back to the system setting. */
function savedTheme(): Theme | null {
  try {
    const value = localStorage.getItem('theme');
    return value === 'light' || value === 'dark' ? value : null;
  } catch {
    return null;
  }
}

/**
 * Switches between the light and dark themes. Until the reader picks one, the page follows the system setting.
 * The choice is kept in this browser only and applied before first paint by a small script in index.html.
 */
export function ThemeToggle() {
  const [picked, setPicked] = useState<Theme | null>(savedTheme);
  const [system, setSystem] = useState<Theme>(systemTheme);
  const theme = picked ?? system;

  useEffect(() => {
    const media = window.matchMedia(darkQuery);
    const update = () => setSystem(media.matches ? 'dark' : 'light');
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);

  useEffect(() => {
    if (picked) document.documentElement.dataset.theme = picked;
    else delete document.documentElement.dataset.theme;
  }, [picked]);

  const next: Theme = theme === 'dark' ? 'light' : 'dark';
  const choose = () => {
    setPicked(next);
    try { localStorage.setItem('theme', next); } catch { /* The choice still applies for this visit. */ }
  };

  return <button type="button" className="theme-toggle" onClick={choose} aria-label={`Switch to ${next} theme`} title={`Switch to ${next} theme`}>
    {theme === 'dark'
      ? <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>
      : <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>}
  </button>;
}
