export function capitalizeFirstLetter(string: string): string {
  if (!string) return string; // Handle empty or invalid input safely
  return string.charAt(0).toUpperCase() + string.slice(1);
}
