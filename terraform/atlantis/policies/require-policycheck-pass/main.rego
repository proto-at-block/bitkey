package main

deny[msg] {
	passed := input.Passed
	req_approvals := input.ReqApprovals
	cur_approvals := input.CurApprovals

	passed == false
	cur_approvals < req_approvals

	msg := sprintf("policy check %s must have passed or have the required number of approvals", [input.PolicySetName])
}
