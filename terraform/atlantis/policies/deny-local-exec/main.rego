package main

import rego.v1

local_exec_provisioners contains resource if {
	[path, value] := walk(input)

	resource := value.resources[_]
	provisioner := resource.provisioners[_]
	provisioner.type == "local-exec"
}

deny contains msg if {
	count(local_exec_provisioners) > 0
	msg := "local-exec provisioners cannot be used"
}
