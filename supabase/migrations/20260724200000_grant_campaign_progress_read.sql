-- The public listing view invokes this SECURITY DEFINER helper for the
-- authenticated reader. Keep the helper itself non-callable to anonymous
-- clients, but allow authenticated reads through the view.
grant execute on function public.campaign_active_tester_count(uuid) to authenticated;
