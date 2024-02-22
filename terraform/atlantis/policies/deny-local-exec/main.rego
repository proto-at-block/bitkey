package main

local_exec_provisioners[resource] {
	[path, value] := walk(input)

	resource := value.resources[_]
	provisioner := resource.provisioners[_]
	provisioner.type == "local-exec"
}

deny[msg] {
	count(local_exec_provisioners) > 0
	msg := "local-exec provisioners cannot be used"
}
