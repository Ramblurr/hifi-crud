const { computePosition, flip, size, apply } = window.FloatingUIDOM;

/*
 * Creates a floating popover menu positioned relative to a trigger element.
 * Uses the native HTML popover API for showing/hiding but FloatingUI for positioning,
 * since CSS anchoring isn't widely supported yet.
 *
 * You will need to add your own popover/popovertarget and aria attributes.
 *
 * optionally your [popover] element can contain the following data attributes:
 *
 * - data-match-reference-width: if present, the popover will match the width of the trigger element
 * - data-placement: the placement of the popover relative to the trigger element, default "bottom"
 *                   Placement values: top, top-start, top-end, right, right-start,
 *                                     right-end, bottom, bottom-start, bottom-end,
 *                                     left, left-start, left-end
 *
 */

export function ActionMenuPopover(container) {
  const trigger = container.querySelector("[popovertarget]");
  const popover = container.querySelector("[popover]");

  // check for existence of data-match-reference-width
  const matchReferenceWidth = popover.hasAttribute(
    "data-match-reference-width",
  );
  const placement = popover.getAttribute("data-placement") || "bottom";

  const update = () => {
    computePosition(trigger, popover, {
      placement: placement,
      middleware: [
        // if there isn't enough space on the bottom, flip to the top
        flip(),
        // the width of the popover should be the same as the trigger/reference
        matchReferenceWidth
          ? size({
              apply({ rects, elements }) {
                Object.assign(elements.floating.style, {
                  minWidth: `${rects.reference.width}px`,
                });
              },
            })
          : null,
      ],
    }).then(({ x, y }) => {
      Object.assign(popover.style, {
        left: `${x}px`,
        top: `${y}px`,
      });
    });
  };
  popover.addEventListener("toggle", update);
}
