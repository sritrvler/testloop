-- The public listing view calls this security-definer helper. PostgREST still
-- requires EXECUTE on the helper for the authenticated role.
grant execute on function public.public_reserved_count(uuid) to authenticated;
