export function BrandMark({
  className = 'size-6',
}: {
  className?: string;
}) {
  return (
    <svg
      viewBox="0 0 64 64"
      aria-hidden="true"
      className={className}
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <circle cx="30" cy="34" r="16" fill="#4F7CFF" />
      <circle cx="24" cy="28" r="4" fill="#9DB5FF" fillOpacity="0.9" />
      <ellipse
        cx="32"
        cy="35"
        rx="26"
        ry="8.5"
        transform="rotate(-12 32 35)"
        stroke="#9DB5FF"
        strokeWidth="4"
      />
      <ellipse
        cx="32"
        cy="35"
        rx="26"
        ry="8.5"
        transform="rotate(-12 32 35)"
        stroke="#1F2A44"
        strokeOpacity="0.14"
        strokeWidth="1.5"
      />
      <path
        d="M49 13l1.6 4.4L55 19l-4.4 1.6L49 25l-1.6-4.4L43 19l4.4-1.6L49 13z"
        fill="#FFC857"
      />
    </svg>
  );
}
