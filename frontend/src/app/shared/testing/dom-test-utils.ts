/** Finds a `<button>` by its trimmed text content. Typed narrowly to avoid `any` leaking from
 * ComponentFixture#nativeElement in spec files. */
export function findButtonByText(root: HTMLElement, text: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll('button')).find(
    (button) => button.textContent?.trim() === text,
  );
}
